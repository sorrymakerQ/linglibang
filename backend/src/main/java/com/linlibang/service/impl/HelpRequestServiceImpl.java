package com.linlibang.service.impl;

import cn.hutool.json.JSONUtil;
import com.linlibang.entity.Order;
import com.linlibang.mapper.CategoryMapper;
import com.linlibang.mapper.HelpRequestMapper;
import com.linlibang.mapper.UserMapper;
import com.linlibang.dto.HelpRequestDTO;
import com.linlibang.dto.Result;
import com.linlibang.entity.Category;
import com.linlibang.entity.HelpRequest;
import com.linlibang.entity.User;
import com.linlibang.service.HelpRequestService;
import com.linlibang.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 求助服务实现类
 *
 * Redis 缓存策略：
 *   1. 每条求助独立缓存：help:item:{id}，TTL 30分钟
 *   2. 首页列表先查 Redis → 未命中才查 MySQL → 回写 Redis
 *   3. 浏览次数 Redis 计数 → 定时异步刷回 MySQL
 */
@Slf4j
@Service
public class HelpRequestServiceImpl implements HelpRequestService {

    @Resource
    private HelpRequestMapper helpRequestMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private com.linlibang.mapper.OrderMapper orderMapper;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private RedissonClient redissonClient;

    /** 定位兜底：前端未提供坐标时使用（application.yml 里配置） */
    @org.springframework.beans.factory.annotation.Value("${linlibang.default-location.lng:116.397428}")
    private Double defaultLng;
    @org.springframework.beans.factory.annotation.Value("${linlibang.default-location.lat:39.90923}")
    private Double defaultLat;
    @org.springframework.beans.factory.annotation.Value("${linlibang.default-location.address:北京市东城区天安门广场}")
    private String defaultAddress;

    /** 单条求助缓存前缀 */
    private static final String HELP_ITEM_KEY = "help:item:";
    /** 分页列表缓存前缀 */
    private static final String HELP_PAGE_KEY = "help:page:";
    /** Redis GEO Key 前缀 */
    private static final String HELP_GEO_KEY = "help:location";
    /** 浏览次数 Key */
    private static final String HELP_VIEW_KEY = "help:views:";
    /** 缓存过期时间（分钟） */
    private static final long CACHE_TTL = 30;
    /** 空值缓存 TTL（分钟）——确认 DB 中不存在的 ID，缓存空标记防止穿透 */
    private static final long NULL_CACHE_TTL = 1;
    /** 默认搜索半径（公里） */
    private static final int DEFAULT_RADIUS = 5;
    /** 哨兵对象：标记"DB 中确认不存在"，区别于 null（未缓存） */
    private static final HelpRequest NOT_EXIST = new HelpRequest();

    // ==================== 缓存读写 ====================

    /** 从 Redis 取单条求助，返回 null=未缓存，NOT_EXIST=确认不存在 */
    private HelpRequest getFromCache(Long id) {
        String json = redisUtils.get(HELP_ITEM_KEY + id);
        if (json == null) {
            return null;
        }
        // 空值标记：之前查过 DB 确认不存在
        if ("{}".equals(json)) {
            return null;
        }
        return JSONUtil.toBean(json, HelpRequest.class);
    }

    /** 写一条求助到 Redis */
    private void setToCache(HelpRequest help) {
        redisUtils.set(HELP_ITEM_KEY + help.getId(),
                JSONUtil.toJsonStr(help), CACHE_TTL, TimeUnit.MINUTES);
    }

    /** 删一条缓存 */
    private void delCache(Long id) {
        redisUtils.delete(HELP_ITEM_KEY + id);
    }

    /** 清除所有分页列表缓存（数据变更时调用，使用 SCAN 避免阻塞） */
    private void clearPageCache() {
        Set<String> keys = redisUtils.scanKeys(HELP_PAGE_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            redisUtils.delete(keys);
        }
    }

    // ==================== 缓存预热 ====================

    /**
     * 应用启动完毕后异步预热缓存
     * 将首页热点数据提前加载到 Redis，用户第一次访问就是缓存命中
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void preloadCache() {
        try {
            List<HelpRequest> list = helpRequestMapper.selectPage(0, 50, null);
            for (HelpRequest h : list) {
                setToCache(h);
                // 同步浏览次数到 Redis，防止重启后浏览计数归零
                if (h.getViewCount() != null && h.getViewCount() > 0) {
                    redisUtils.set(HELP_VIEW_KEY + h.getId(), String.valueOf(h.getViewCount()));
                }
            }
            log.info("缓存预热完成：{} 条求助已加载到 Redis", list.size());
        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }

    // ==================== 互斥锁防缓存击穿 ====================

    /**
     * 带互斥锁的缓存查询（防缓存击穿）
     *
     * 问题：热点数据过期瞬间，大量请求同时穿透到 MySQL
     * 解决：只让第一个抢到锁的请求查 DB 并回写缓存，其余请求等锁后走双重检查命中缓存；
     *      等不到锁则降级返回空，绝不兜底查 DB，避免击穿演变成 DB 雪崩
     */
    private HelpRequest getWithMutex(Long id) {
        // ① 先查 Redis
        HelpRequest cached = getFromCache(id);
        if (cached != null) return cached;

        // ② 未命中 -> tryLock 最多等 500ms（线程被高效挂起、锁释放即唤醒，看门狗自动续期，无需手设 TTL）
        RLock mutexLock = redissonClient.getLock("lock:help:" + id);
        boolean locked;
        try {
            locked = mutexLock.tryLock(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        if (locked) {
            try {
                // ③ 抢到锁 -> 再次检查 Redis（双重检查，防止前面的人已经写进去了）
                cached = getFromCache(id);
                if (cached != null) return cached;

                // ④ 查数据库并回写缓存
                HelpRequest fromDb = helpRequestMapper.selectById(id);
                if (fromDb != null) {
                    setToCache(fromDb);
                }
                return fromDb;
            } finally {
                mutexLock.unlock();
            }
        }

        // ⑤ 等满 500ms 仍未拿到锁 -> 再查一次缓存（锁持有者可能刚写完），命中则返回
        cached = getFromCache(id);
        if (cached != null) return cached;

        // 仍没有则返回空由调用方降级（用户重试即可）；绝不兜底查 DB，避免击穿演变成 DB 雪崩
        return null;
    }

    // ==================== 业务方法 ====================

    @Override
    @Transactional
    public Result publishHelp(HelpRequestDTO dto, Long userId) {
        // 定位兜底：前端未提供有效坐标时用配置的默认值，保证入库和 GEO 都不为空
        // （前端可能因浏览器不支持 / 用户拒绝授权 / 定位超时导致 lng=lat=0 或 null）
        boolean lngInvalid = dto.getLng() == null || dto.getLng() == 0.0;
        boolean latInvalid = dto.getLat() == null || dto.getLat() == 0.0;
        if (lngInvalid || latInvalid) {
            dto.setLng(defaultLng);
            dto.setLat(defaultLat);
            if (dto.getAddress() == null || dto.getAddress().trim().isEmpty()) {
                dto.setAddress(defaultAddress);
            }
            log.warn("publishHelp: 用户 {} 未提供坐标，兜底为默认位置 ({}, {})",
                    userId, defaultLng, defaultLat);
        }

        HelpRequest help = new HelpRequest();
        help.setUserId(userId);
        help.setCategoryId(dto.getCategoryId());
        help.setTitle(dto.getTitle());
        help.setDescription(dto.getDescription());
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            help.setImages(String.join(",", dto.getImages()));
        }
        help.setReward(dto.getReward());
        help.setAddress(dto.getAddress());
        help.setLng(dto.getLng());
        help.setLat(dto.getLat());
        help.setUrgent(dto.getUrgent() != null ? dto.getUrgent() : 0);
        help.setStatus(1);
        help.setViewCount(0);

        // 1. 写入 MySQL
        helpRequestMapper.insert(help);

        // 2. 注册事务回调：提交成功后才写 Redis，回滚则跳过
        //    避免 MySQL 回滚后 Redis 残留幻影数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                setToCache(help);
                redisUtils.geoAdd(HELP_GEO_KEY, dto.getLng(), dto.getLat(), help.getId().toString());
                clearPageCache();
            }
        });

        return Result.ok("求助发布成功", help.getId());
    }

    @Override
    public Result getNearbyHelp(Double lng, Double lat, Integer radius, Integer page, Integer size,
                                Long categoryId, String keyword) {
        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 10;

        // 没有位置 → 走分页/搜索
        if (lng == null || lat == null) {
            // 有搜索关键词 → 走搜索，不走缓存
            if (keyword != null && !keyword.trim().isEmpty()) {
                return searchHelp(keyword, categoryId, page, size);
            }

            // ① 查分页缓存（Key 后缀区分分类）
            String cacheSuffix = (categoryId != null ? ":c" + categoryId : "") + ":s" + pageSize;
            String pageKey = HELP_PAGE_KEY + pageNum + cacheSuffix;
            String cachedJson = redisUtils.get(pageKey);
            if (cachedJson != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedResult = JSONUtil.toBean(cachedJson, Map.class);
                return Result.ok(cachedResult);
            }

            // ② 未命中 → 查 MySQL（带分类筛选）
            List<HelpRequest> list = helpRequestMapper.selectPage((pageNum - 1) * pageSize, pageSize, categoryId);
            Long total = helpRequestMapper.selectCount(categoryId);

            // ③批量查发布者 + 分类（避免 N+1），组装成前端需要的富对象
            List<Map<String, Object>> enriched = enrichHelpList(list, null);

            // ④回写 Redis 分页缓存（5分钟 + 随机0~60秒，避免大量缓存同时过期引发雪崩）
            Map<String, Object> result = new HashMap<>();
            result.put("list", enriched);
            result.put("total", total);
            long pageTtlSeconds = 300 + (long) (Math.random() * 60);
            redisUtils.set(pageKey, JSONUtil.toJsonStr(result), pageTtlSeconds, TimeUnit.SECONDS);
            // ⑤同时把每条求助也缓存
            for (HelpRequest h : list) {
                setToCache(h);
            }

            return Result.ok(result);
        }

        // ① GEO 搜索附近 ID
        int searchRadius = radius != null ? radius : DEFAULT_RADIUS;
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResults =
                redisUtils.geoSearch(HELP_GEO_KEY, lng, lat, searchRadius);

        if (geoResults.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("list", Collections.emptyList());
            result.put("total", 0);
            return Result.ok("附近暂无求助", result);
        }

        // ② 收集 ID + 距离
        List<Long> helpIds = geoResults.stream()
                .map(r -> Long.valueOf(r.getContent().getName()))
                .collect(Collectors.toList());

        Map<Long, Double> distanceMap = geoResults.stream()
                .collect(Collectors.toMap(
                        r -> Long.valueOf(r.getContent().getName()),
                        r -> r.getDistance().getValue() * 1000));

        // ③ 分化：从 Redis 命中的 + 未命中的
        List<HelpRequest> cachedList = new ArrayList<>();
        List<Long> missedIds = new ArrayList<>();

        for (Long id : helpIds) {
            HelpRequest cached = getFromCache(id);
            if (cached != null) {
                cachedList.add(cached);
            } else {
                missedIds.add(id);
            }
        }

        // ④ 未命中 → 查 MySQL → 回写 Redis
        List<HelpRequest> dbList = Collections.emptyList();
        if (!missedIds.isEmpty()) {
            dbList = helpRequestMapper.selectByIdsAndStatus(missedIds, 1);
            for (HelpRequest h : dbList) {
                setToCache(h);  // 回写缓存，下次直接命中
            }
        }

        // ⑤ 合并（Redis 命中 + DB 回源），过滤 status=1
        List<HelpRequest> allList = new ArrayList<>(cachedList);
        allList.addAll(dbList);
        allList.removeIf(h -> h.getStatus() != 1);

        // ⑤½ 关键词过滤（GEO 搜索不支持全文检索，在内存中过滤）
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            allList.removeIf(h ->
                !(h.getTitle() != null && h.getTitle().toLowerCase().contains(kw)) &&
                !(h.getDescription() != null && h.getDescription().toLowerCase().contains(kw))
            );
        }

        // ⑥ 批量查询发布者和分类 + 组装成前端富对象
        List<Map<String, Object>> resultList = enrichHelpList(allList, distanceMap);

        // ⑦ 按距离排序 + 分页
        resultList.sort(Comparator.comparingDouble(m -> (Double) m.get("distance")));

        int total = resultList.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> paged = from < total
                ? resultList.subList(from, to) : Collections.emptyList();

        Map<String, Object> result = new HashMap<>();
        result.put("list", paged);
        result.put("total", total);
        return Result.ok(result);
    }

    /**
     * 将 List&lt;HelpRequest&gt; 组装成前端需要的富对象列表。
     * 一次性批量查询发布者和分类（避免 N+1），把 publisherName / publisherAvatar
     * / categoryName / categoryIcon / distance 全都拼上去。
     *
     * @param helps       原始求助列表
     * @param distanceMap 距离表（附近搜索场景才有；null 表示无距离信息）
     */
    private List<Map<String, Object>> enrichHelpList(List<HelpRequest> helps,
                                                     Map<Long, Double> distanceMap) {
        if (helps == null || helps.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询发布者和分类（空集合守卫防 SQL 语法错误）
        List<Long> userIds = helps.stream()
                .map(HelpRequest::getUserId).distinct().collect(Collectors.toList());
        List<Long> categoryIds = helps.stream()
                .map(HelpRequest::getCategoryId).distinct().collect(Collectors.toList());

        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, Category> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectByIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));

        List<Map<String, Object>> resultList = new ArrayList<>(helps.size());
        for (HelpRequest help : helps) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", help.getId());
            item.put("userId", help.getUserId());
            item.put("categoryId", help.getCategoryId());
            item.put("title", help.getTitle());
            item.put("description", help.getDescription());
            item.put("images", help.getImages());
            item.put("reward", help.getReward());
            item.put("address", help.getAddress());
            item.put("lng", help.getLng());
            item.put("lat", help.getLat());
            item.put("urgent", help.getUrgent());
            item.put("status", help.getStatus());
            item.put("createTime", help.getCreateTime());
            item.put("distance", distanceMap != null
                    ? distanceMap.getOrDefault(help.getId(), 0.0)
                    : 0.0);

            User publisher = userMap.get(help.getUserId());
            if (publisher != null) {
                item.put("publisherName", publisher.getNickname());
                item.put("publisherAvatar", publisher.getAvatar());
                item.put("publisherCredit", publisher.getCredit());
            }

            Category category = categoryMap.get(help.getCategoryId());
            if (category != null) {
                item.put("categoryName", category.getName());
                item.put("categoryIcon", category.getIcon());
            }

            resultList.add(item);
        }
        return resultList;
    }

    @Override
    public Result getHelpById(Long helpId) {
        // ① 带互斥锁查缓存（防击穿：同一时间只有一个线程查 DB）
        HelpRequest help = getWithMutex(helpId);
        if (help == null) {
            return Result.fail("求助不存在或已删除");
        }

        // ③ Redis 原子自增浏览次数 + 异步刷回 MySQL
        Long newViewCount = redisUtils.increment(HELP_VIEW_KEY + helpId);
        help.setViewCount(newViewCount.intValue());
        setToCache(help);
        syncViewToDb(helpId, newViewCount.intValue());

        // ⑤ 组装详情
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", help.getId());
        detail.put("userId", help.getUserId());
        detail.put("title", help.getTitle());
        detail.put("description", help.getDescription());
        detail.put("images", help.getImages() != null
                ? Arrays.asList(help.getImages().split(","))
                : Collections.emptyList());
        detail.put("reward", help.getReward());
        detail.put("address", help.getAddress());
        detail.put("lng", help.getLng());
        detail.put("lat", help.getLat());
        detail.put("status", help.getStatus());
        detail.put("urgent", help.getUrgent());
        detail.put("viewCount", newViewCount);
        detail.put("createTime", help.getCreateTime());

        User publisher = userMapper.selectById(help.getUserId());
        if (publisher != null) {
            detail.put("publisherName", publisher.getNickname());
            detail.put("publisherAvatar", publisher.getAvatar());
            detail.put("publisherCredit", publisher.getCredit());
            detail.put("publisherHelpCount", publisher.getHelpCount());
        }

        Category category = categoryMapper.selectById(help.getCategoryId());
        if (category != null) {
            detail.put("categoryName", category.getName());
            detail.put("categoryIcon", category.getIcon());
        }

        // 查询当前接单人的信息（如果有活跃订单）
        Order activeOrder = orderMapper.selectActiveByHelpId(helpId);
        if (activeOrder != null) {
            detail.put("currentHelperId", activeOrder.getHelperId());
            detail.put("currentOrderId", activeOrder.getId());
        }

        return Result.ok(detail);
    }

    @Override
    @Transactional
    public Result cancelHelp(Long helpId, Long userId) {
        HelpRequest help = helpRequestMapper.selectById(helpId);
        if (help == null) return Result.fail("求助不存在");
        if (!help.getUserId().equals(userId)) return Result.fail("只能取消自己发布的求助");
        if (help.getStatus() != 1) return Result.fail("当前状态不允许取消");

        help.setStatus(4);
        helpRequestMapper.updateById(help);

        // 更新 Redis 缓存（而非删除，保留数据）
        setToCache(help);
        redisUtils.geoRemove(HELP_GEO_KEY, helpId.toString());
        clearPageCache();

        return Result.ok("求助已取消");
    }

    @Override
    public Result getMyHelp(Long userId, Integer page, Integer size) {
        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 10;
        int offset = (pageNum - 1) * pageSize;

        List<HelpRequest> list = helpRequestMapper.selectByUserIdPaged(userId, offset, pageSize);
        Long total = helpRequestMapper.selectCountByUserId(userId);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("list", list);
        resultMap.put("total", total);
        return Result.ok(resultMap);
    }

    // ==================== 异步刷浏览量 ====================

    /**
     * 异步将 Redis 中的浏览量刷回 MySQL
     */
    @Async
    public void syncViewToDb(Long helpId, int count) {
        try {
            HelpRequest h = new HelpRequest();
            h.setId(helpId);
            h.setViewCount(count);
            helpRequestMapper.updateById(h);
            log.debug("浏览量异步刷库: helpId={}, count={}", helpId, count);
        } catch (Exception e) {
            log.error("浏览量刷库失败: helpId={}", helpId, e);
        }
    }

    /**
     * 定时任务：每5分钟扫描所有浏览计数 Key，批量同步到 MySQL
     * 兜底策略：防止异步写库丢失
     */
    @Scheduled(fixedRate = 300000)
    public void batchSyncViews() {
        // 使用 SCAN 扫描 Redis 中所有 help:views:* 的 Key（非阻塞）
        Set<String> keys = redisUtils.scanKeys(HELP_VIEW_KEY + "*");
        if (keys == null || keys.isEmpty()) return;

        int count = 0;
        for (String key : keys) {
            try {
                String idStr = key.substring(HELP_VIEW_KEY.length());
                Long helpId = Long.valueOf(idStr);
                String val = redisUtils.get(key);
                if (val != null) {
                    int viewCount = Integer.parseInt(val);
                    HelpRequest h = new HelpRequest();
                    h.setId(helpId);
                    h.setViewCount(viewCount);
                    helpRequestMapper.updateById(h);
                    count++;
                }
            } catch (Exception e) {
                log.error("批量刷浏览量失败: key={}", key, e);
            }
        }
        if (count > 0) {
            log.info("定时批量刷浏览量完成: {} 条", count);
        }
    }

    // ==================== Redis ↔ MySQL 定时对账 ====================

    /**
     * 每 10 分钟执行一次 Redis 与 MySQL 对账
     *
     * 正向同步（MySQL → Redis）：确保所有待接单求助的缓存和 GEO 位置存在
     * 反向清理（Redis → MySQL）：删除 MySQL 中已不存在或已取消的过期缓存
     *
     * 设计原则：MySQL 是唯一数据源，Redis 是缓存加速层。
     * 对账任务作为兜底，修复 Canal 断连、afterCommit 丢失等异常场景。
     */
    @Scheduled(fixedRate = 600000)  // 10 分钟
    public void reconcileCache() {
        log.info("========== 开始对账 Redis ↔ MySQL ==========");
        int cacheFilled = 0;
        int geoFilled = 0;
        int cacheCleaned = 0;
        int geoCleaned = 0;

        // ==================== 正向同步 ====================

        // ① 缓存对账：遍历 MySQL 中所有待接单的求助，确保 Redis 缓存存在
        Set<String> cachedKeys = redisUtils.scanKeys(HELP_ITEM_KEY + "*");
        Set<Long> cachedIds = Collections.emptySet();
        if (cachedKeys != null) {
            cachedIds = cachedKeys.stream()
                    .map(k -> Long.valueOf(k.substring(HELP_ITEM_KEY.length())))
                    .collect(Collectors.toSet());
        }

        // 分页查询 MySQL 中所有 status=1（待接单）的求助
        int batchSize = 200;
        int offset = 0;
        while (true) {
            List<HelpRequest> batch = helpRequestMapper.selectPage(offset, batchSize, null);
            if (batch.isEmpty()) break;

            for (HelpRequest h : batch) {
                // 只检查待接单的求助（已完成/已取消的不强制缓存）
                if (h.getStatus() == null || h.getStatus() != 1) continue;

                // 缓存缺失 → 补写
                if (!cachedIds.contains(h.getId())) {
                    setToCache(h);
                    cacheFilled++;
                }

                // GEO 缺失 → 补写（用 GEOPOS 检查存在性，避免 Redisson geoDistance NPE bug）
                if (!redisUtils.geoExists(HELP_GEO_KEY, h.getId().toString())
                        && h.getLng() != null && h.getLat() != null) {
                    redisUtils.geoAdd(HELP_GEO_KEY, h.getLng(), h.getLat(), h.getId().toString());
                    geoFilled++;
                }
            }
            offset += batchSize;
        }

        // ==================== 反向清理 ====================

        // ② 缓存清理：Redis 中有但 MySQL 中已删除/不存在的 key → 删除
        if (cachedKeys != null && !cachedKeys.isEmpty()) {
            List<Long> idsToCheck = new ArrayList<>(cachedIds);
            // 分批查 MySQL
            for (int i = 0; i < idsToCheck.size(); i += 500) {
                int to = Math.min(i + 500, idsToCheck.size());
                List<Long> subIds = idsToCheck.subList(i, to);
                List<HelpRequest> existing = helpRequestMapper.selectByIds(subIds);
                Set<Long> existingIds = existing.stream()
                        .map(HelpRequest::getId).collect(Collectors.toSet());

                for (Long id : subIds) {
                    if (!existingIds.contains(id)) {
                        delCache(id);
                        cacheCleaned++;
                    }
                }
            }
        }

        // ③ GEO 清理：GEO 中但 MySQL 不存在或状态不是待接单的 → 删除
        Set<String> geoMembersRaw = redisUtils.zSetRange(HELP_GEO_KEY, 0, -1);
        if (geoMembersRaw != null && !geoMembersRaw.isEmpty()) {
            List<Long> geoIds = geoMembersRaw.stream()
                    .map(Long::valueOf).collect(Collectors.toList());

            for (int i = 0; i < geoIds.size(); i += 500) {
                int to = Math.min(i + 500, geoIds.size());
                List<Long> subIds = geoIds.subList(i, to);
                List<HelpRequest> existing = helpRequestMapper.selectByIds(subIds);
                // GEO 里只保留 status=1（待接单）的求助
                Set<Long> validIds = existing.stream()
                        .filter(h -> h.getStatus() != null && h.getStatus() == 1)
                        .map(HelpRequest::getId).collect(Collectors.toSet());

                for (Long id : subIds) {
                    if (!validIds.contains(id)) {
                        redisUtils.geoRemove(HELP_GEO_KEY, id.toString());
                        geoCleaned++;
                    }
                }
            }
        }

        log.info("对账完成：缓存补写{} | GEO补写{} | 缓存清理{} | GEO清理{}",
                cacheFilled, geoFilled, cacheCleaned, geoCleaned);
    }

    @Override
    public Result searchHelp(String keyword, Long categoryId, Integer page, Integer size) {
        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 10;
        int offset = (pageNum - 1) * pageSize;

        List<HelpRequest> list = helpRequestMapper.search(keyword, categoryId, offset, pageSize);
        Long total = helpRequestMapper.searchCount(keyword, categoryId);

        // 批量查发布者 + 分类，返回富对象（与首页列表结构一致）
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("list", enrichHelpList(list, null));
        resultMap.put("total", total);
        return Result.ok(resultMap);
    }
}
