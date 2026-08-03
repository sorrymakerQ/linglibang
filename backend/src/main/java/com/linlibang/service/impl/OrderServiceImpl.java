package com.linlibang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.linlibang.config.RabbitMQConfig;
import com.linlibang.mapper.HelpRequestMapper;
import com.linlibang.mapper.OrderMapper;
import com.linlibang.mapper.UserMapper;
import com.linlibang.dto.Result;
import com.linlibang.entity.HelpRequest;
import com.linlibang.entity.Order;
import com.linlibang.entity.User;
import com.linlibang.service.OrderService;
import com.linlibang.utils.RedisUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 * 核心流程：接单 → 进行中 → 完成 → 评价
 * 使用 JdbcTemplate DAO 进行数据库操作，所有 SQL 手写
 */
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private HelpRequestMapper helpRequestMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private PlatformTransactionManager transactionManager;

    /** 编程式事务模板：让分布式锁能完整包裹事务（锁在事务外，unlock 在事务提交之后） */
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    private void initTransactionTemplate() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 订单分布式锁前缀 */
    private static final String ORDER_LOCK_PREFIX = "order:lock:";
    /** 求助分页缓存前缀 */
    private static final String HELP_PAGE_KEY = "help:page:";

    /** 清除求助分页缓存（数据变更时保持 Redis 与 DB 一致） */
    private void clearHelpCache() {
        Set<String> keys = redisUtils.scanKeys(HELP_PAGE_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            redisUtils.delete(keys);
        }
    }

    @Override
    public Result acceptOrder(Long helpId, Long helperId) {
        // 1. Redisson 分布式锁防重复接单。锁必须在事务外：
        //    若锁在 @Transactional 内，unlock（finally）会早于事务提交，下个线程拿到锁时读到旧状态 -> 重复接单。
        //    故改用编程式事务：execute 返回时事务已提交，随后才 unlock，锁的生命周期完整覆盖事务。
        RLock lock = redissonClient.getLock(ORDER_LOCK_PREFIX + helpId);
        if (!lock.tryLock()) {
            return Result.fail("该求助已被其他人接单，请刷新后再试");
        }
        try {
            return transactionTemplate.execute(status -> doAcceptOrderInTx(helpId, helperId));
        } finally {
            lock.unlock();
        }
    }

    /** 接单的事务内逻辑（由 acceptOrder 在分布式锁内、通过 TransactionTemplate 调用） */
    private Result doAcceptOrderInTx(Long helpId, Long helperId) {
        // 2. 查询求助信息（使用 SQL 语句：SELECT * FROM tb_help_request WHERE id = ?）
        HelpRequest help = helpRequestMapper.selectById(helpId);
        if (help == null) {
            return Result.fail("求助不存在或已删除");
        }

        // 3. 校验状态（只有待接单的才能接）
        if (help.getStatus() != 1) {
            return Result.fail("该求助已被接单或已取消");
        }

        // 4. 不能接自己的求助
        if (help.getUserId().equals(helperId)) {
            return Result.fail("不能接自己发布的求助");
        }

        // 5. 创建订单（使用 SQL 语句：INSERT INTO tb_order (...) VALUES (...)）
        Order order = new Order();
        order.setHelpId(helpId);
        order.setPublisherId(help.getUserId());
        order.setHelperId(helperId);
        order.setStatus(2);  // 进行中
        orderMapper.insert(order);

        // 6. 更新求助状态为进行中
        help.setStatus(2);
        helpRequestMapper.updateById(help);
        // Redis 操作放到事务提交后，避免 DB 回滚后 Redis 残留脏数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisUtils.delete("help:item:" + helpId);
                clearHelpCache();
            }
        });

        // 7. 发送通知给发布者（RabbitMQ 不可用时不影响接单）
        try {
            Map<String, Object> notifData = new HashMap<>();
            notifData.put("userId", help.getUserId());
            notifData.put("title", "有人接单了！");
            notifData.put("content", "您的求助「" + help.getTitle() + "」已被邻居接单");
            notifData.put("type", 2);
            notifData.put("relatedId", order.getId());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_KEY,
                    notifData
            );
        } catch (Exception e) {
            // RabbitMQ 不可用，通知稍后补发
        }

        // 8. 发送延迟消息（30分钟后检查，RabbitMQ 不可用时跳过）
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_EXCHANGE,
                    RabbitMQConfig.ORDER_DELAY_KEY,
                    order.getId()
            );
        } catch (Exception e) {
            // RabbitMQ 不可用，超时自动取消暂时失效
        }

        return Result.ok("接单成功", order.getId());
    }

    @Override
    @Transactional
    public Result cancelOrder(Long orderId, Long userId, String reason) {
        // 1. 查询订单（使用 SQL 语句：SELECT * FROM tb_order WHERE id = ?）
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 2. 只有订单参与者可以取消
        if (!order.getPublisherId().equals(userId) && !order.getHelperId().equals(userId)) {
            return Result.fail("无权取消此订单");
        }

        // 3. 只能取消"已接单"或"进行中"状态的订单
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            return Result.fail("当前订单状态不允许取消");
        }

        // 4. 更新订单状态（使用 SQL 语句：UPDATE tb_order SET status = 4 WHERE id = ?）
        order.setStatus(4);  // 已取消
        order.setCancelReason(reason);
        orderMapper.updateById(order);

        // 5. 恢复求助状态为"待接单"
        HelpRequest help = helpRequestMapper.selectById(order.getHelpId());
        if (help != null && help.getStatus() == 2) {
            help.setStatus(1);
            helpRequestMapper.updateById(help);
            // Redis 操作放到事务提交后，避免 DB 回滚后 Redis 残留脏数据
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisUtils.delete("help:item:" + help.getId());
                    clearHelpCache();
                    redisUtils.geoAdd("help:location", help.getLng(), help.getLat(), help.getId().toString());
                }
            });
        }

        // 6. 扣除取消方信用分
        User cancelUser = userMapper.selectById(userId);
        if (cancelUser != null && cancelUser.getCredit() > 0) {
            cancelUser.setCredit(Math.max(0, cancelUser.getCredit() - 5));  // 扣5分，不低于0
            userMapper.updateById(cancelUser);
        }

        return Result.ok("订单已取消");
    }

    @Override
    @Transactional
    public Result finishOrder(Long orderId, Long userId) {
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 2. 只有发布者可以确认完成
        if (!order.getPublisherId().equals(userId)) {
            return Result.fail("只有发布者可以确认完成");
        }

        // 3. 只能完成"进行中"的订单
        if (order.getStatus() != 2) {
            return Result.fail("当前状态不允许完成");
        }

        // 4. 更新订单状态（使用 SQL 语句：UPDATE tb_order SET status = 3 WHERE id = ?）
        order.setStatus(3);  // 已完成
        order.setFinishTime(java.time.LocalDateTime.now());
        orderMapper.updateById(order);

        // 5. 更新求助状态
        HelpRequest help = helpRequestMapper.selectById(order.getHelpId());
        if (help != null) {
            help.setStatus(3);
            helpRequestMapper.updateById(help);
            // Redis 操作放到事务提交后，避免 DB 回滚后 Redis 残留脏数据
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisUtils.delete("help:item:" + help.getId());
                    clearHelpCache();
                }
            });
        }



        // 6. 增加接单者信用分和帮助次数（使用 SQL 语句：UPDATE tb_user SET credit = ? WHERE id = ?）
        User helper = userMapper.selectById(order.getHelperId());
        if (helper != null) {
            helper.setCredit(helper.getCredit() + 10);  // 加10分
            helper.setHelpCount(helper.getHelpCount() + 1);
            userMapper.updateById(helper);
        }

        // 7. 发送评价提醒通知（RabbitMQ 不可用时不影响完成）
        try {
            Map<String, Object> notifData = new HashMap<>();
            notifData.put("userId", order.getPublisherId());
            notifData.put("title", "订单已完成");
            notifData.put("content", "请对邻居的服务进行评价");
            notifData.put("type", 3);
            notifData.put("relatedId", orderId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_KEY,
                    notifData
            );
        } catch (Exception e) {
            // RabbitMQ 不可用
        }

        return Result.ok("订单已完成，请评价");
    }

    @Override
    @Transactional
    public Result reviewOrder(Long orderId, Long userId, Integer score, String comment) {
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 2. 校验评分（null 安全，防止 NPE）
        if (score == null || score < 1 || score > 5) {
            return Result.fail("评分范围为1-5分");
        }

        // 3. 发布者评价接单者
        if (order.getPublisherId().equals(userId)) {
            if (order.getPublisherScore() != null) {
                return Result.fail("已经评价过了");
            }
            order.setPublisherScore(score);
            order.setPublisherComment(comment);
        }
        // 4. 接单者评价发布者
        else if (order.getHelperId().equals(userId)) {
            if (order.getHelperScore() != null) {
                return Result.fail("已经评价过了");
            }
            order.setHelperScore(score);
            order.setHelperComment(comment);
        } else {
            return Result.fail("无权评价此订单");
        }

        // 5. 双方都评价后，订单状态变为"已评价"
        if (order.getPublisherScore() != null && order.getHelperScore() != null) {
            order.setStatus(5);  // 已评价
        }

        orderMapper.updateById(order);

        return Result.ok("评价成功");
    }

    @Override
    public Result getOrderById(Long orderId, Long userId) {
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 越权校验：仅订单参与方可查看详情，防止遍历订单 ID 获取他人手机号
        if (!order.getPublisherId().equals(userId) && !order.getHelperId().equals(userId)) {
            return Result.fail(403, "无权查看此订单");
        }

        // 2. 组装详情
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", order.getId());
        detail.put("helpId", order.getHelpId());
        detail.put("publisherId", order.getPublisherId());
        detail.put("helperId", order.getHelperId());
        detail.put("status", order.getStatus());
        detail.put("cancelReason", order.getCancelReason());
        detail.put("acceptTime", order.getAcceptTime());
        detail.put("finishTime", order.getFinishTime());
        detail.put("publisherScore", order.getPublisherScore());
        detail.put("helperScore", order.getHelperScore());
        detail.put("publisherComment", order.getPublisherComment());
        detail.put("helperComment", order.getHelperComment());
        detail.put("createTime", order.getCreateTime());

        // 3. 求助信息
        HelpRequest help = helpRequestMapper.selectById(order.getHelpId());
        if (help != null) {
            detail.put("helpTitle", help.getTitle());
            detail.put("helpDescription", help.getDescription());
            detail.put("helpReward", help.getReward());
            detail.put("helpAddress", help.getAddress());
            detail.put("helpStatus", help.getStatus());
        }

        // 4. 发布者信息
        User publisher = userMapper.selectById(order.getPublisherId());
        if (publisher != null) {
            Map<String, Object> publisherInfo = new HashMap<>();
            publisherInfo.put("id", publisher.getId());
            publisherInfo.put("nickname", publisher.getNickname());
            publisherInfo.put("avatar", publisher.getAvatar());
            publisherInfo.put("phone", maskPhone(publisher.getPhone()));
            publisherInfo.put("credit", publisher.getCredit());
            publisherInfo.put("helpCount", publisher.getHelpCount());
            detail.put("publisher", publisherInfo);
        }

        // 5. 接单者信息
        User helper = userMapper.selectById(order.getHelperId());
        if (helper != null) {
            Map<String, Object> helperInfo = new HashMap<>();
            helperInfo.put("id", helper.getId());
            helperInfo.put("nickname", helper.getNickname());
            helperInfo.put("avatar", helper.getAvatar());
            helperInfo.put("phone", maskPhone(helper.getPhone()));
            helperInfo.put("credit", helper.getCredit());
            helperInfo.put("helpCount", helper.getHelpCount());
            detail.put("helper", helperInfo);
        }

        return Result.ok(detail);
    }

    @Override
    public Result getMyOrders(Long userId, String role, Integer page, Integer size) {
        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 10;
        int offset = (pageNum - 1) * pageSize;

        List<Order> orders = orderMapper.selectByUserIdAndRole(userId, role, offset, pageSize);
        Long total = orderMapper.selectCountByUserIdAndRole(userId, role);

        // 批量查询关联的求助和用户信息（避免 N+1 问题）
        // 空集合守卫：MyBatis <foreach> 空 list 会生成 IN () 导致 SQL 语法错误
        Map<Long, HelpRequest> helpMap = Collections.emptyMap();
        Map<Long, User> userMap = Collections.emptyMap();

        if (!orders.isEmpty()) {
            List<Long> helpIds = orders.stream()
                    .map(Order::getHelpId).distinct().collect(Collectors.toList());
            List<Long> otherUserIds = orders.stream()
                    .map(o -> o.getPublisherId().equals(userId) ? o.getHelperId() : o.getPublisherId())
                    .distinct().collect(Collectors.toList());

            if (!helpIds.isEmpty()) {
                helpMap = helpRequestMapper.selectByIds(helpIds).stream()
                        .collect(Collectors.toMap(HelpRequest::getId, h -> h, (a, b) -> a));
            }
            if (!otherUserIds.isEmpty()) {
                userMap = userMapper.selectByIds(otherUserIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            }
        }

        // 组装返回数据（关联求助标题和对方信息）
        List<Map<String, Object>> enrichedList = new ArrayList<>();
        for (Order order : orders) {
            Map<String, Object> item = BeanUtil.beanToMap(order);

            // 求助信息
            HelpRequest help = helpMap.get(order.getHelpId());
            if (help != null) {
                item.put("helpTitle", help.getTitle());
                item.put("reward", help.getReward());
            }

            // 对方信息
            Long otherUserId = order.getPublisherId().equals(userId)
                    ? order.getHelperId()
                    : order.getPublisherId();
            User otherUser = userMap.get(otherUserId);
            if (otherUser != null) {
                item.put("otherName", otherUser.getNickname());
                item.put("otherAvatar", otherUser.getAvatar());
            }

            enrichedList.add(item);
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("list", enrichedList);
        resultMap.put("total", total);

        return Result.ok(resultMap);
    }

    /** 手机号脱敏：138****1234 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
