package com.linlibang.listener;

import com.linlibang.config.RabbitMQConfig;
import com.linlibang.mapper.HelpRequestMapper;
import com.linlibang.mapper.NotificationMapper;
import com.linlibang.mapper.OrderMapper;
import com.linlibang.entity.HelpRequest;
import com.linlibang.entity.Notification;
import com.linlibang.entity.Order;
import com.linlibang.utils.RedisUtils;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * RabbitMQ 消息监听器
 * 处理订单超时、通知发送等异步任务
 * 使用 JdbcTemplate DAO 进行数据库操作
 */
@Slf4j
@Component
public class MqListener {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private HelpRequestMapper helpRequestMapper;

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private RedisUtils redisUtils;

    /**
     * 监听订单死信队列（处理超时订单）
     * 当订单延迟30分钟后仍未完成，自动取消
     */
    @Transactional
    @RabbitListener(queues = RabbitMQConfig.ORDER_DEAD_QUEUE)
    public void handleOrderTimeout(Long orderId, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            // SELECT * FROM tb_order WHERE id = ?
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                channel.basicAck(tag, false);
                return;
            }

            // 如果订单处于"已接单"或"进行中"状态，自动取消
            // 修复：接单时订单状态为2(进行中)，而非1(已接单)
            if (order.getStatus() == 1 || order.getStatus() == 2) {
                // UPDATE tb_order SET status = 4 WHERE id = ?
                order.setStatus(4);  // 已取消
                order.setCancelReason("超时未处理，系统自动取消");
                orderMapper.updateById(order);

                // 恢复求助状态
                HelpRequest help = helpRequestMapper.selectById(order.getHelpId());
                if (help != null && help.getStatus() == 2) {
                    // UPDATE tb_help_request SET status = 1 WHERE id = ?
                    help.setStatus(1);  // 恢复为待接单
                    helpRequestMapper.updateById(help);

                    // Redis 操作放到事务提交后，避免 DB 回滚后 Redis 残留脏数据
                    TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                redisUtils.geoAdd("help:location",
                                    help.getLng(), help.getLat(),
                                    help.getId().toString());
                            }
                        });
                }

                // 幂等去重：避免重复消费时插入重复通知
                if (notificationMapper.selectByRelatedIdAndType(orderId, 1) == null) {
                    Notification notification = new Notification();
                    notification.setUserId(order.getHelperId());
                    notification.setTitle("订单已自动取消");
                    notification.setContent("您在30分钟内未处理订单，系统已自动取消");
                    notification.setType(1);
                    notification.setRelatedId(orderId);
                    notificationMapper.insert(notification);
                }
            }

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("订单超时处理失败, orderId={}", orderId, e);
            try {
                channel.basicNack(tag, false, true);  // 重新入队
            } catch (IOException ex) {
                log.error("basicNack 失败, orderId={}", orderId, ex);
            }
        }
    }

    /**
     * 监听通知队列（保存通知到数据库）
     * SQL: INSERT INTO tb_notification (...) VALUES (...)
     */
    @Transactional
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleNotification(Map<String, Object> data, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            Notification notification = new Notification();
            notification.setUserId(Long.valueOf(data.get("userId").toString()));
            notification.setTitle((String) data.get("title"));
            notification.setContent((String) data.get("content"));
            notification.setType((Integer) data.get("type"));
            notification.setRelatedId(data.get("relatedId") != null
                    ? Long.valueOf(data.get("relatedId").toString())
                    : null);
            notification.setCreateTime(LocalDateTime.now());

            notificationMapper.insert(notification);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("通知消息处理失败, data={}", data, e);
            try {
                channel.basicNack(tag, false, true);
            } catch (IOException ex) {
                log.error("basicNack 失败", ex);
            }
        }
    }
}
