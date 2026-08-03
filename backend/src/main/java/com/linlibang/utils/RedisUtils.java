package com.linlibang.utils;

import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 封装项目实际使用的 Redis 操作（String / Number / GEO / ZSet range / scan）。
 */
@Component
public class RedisUtils {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ==================== 字符串操作 ====================

    /** 存入字符串（带过期时间） */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 存入字符串（不过期，用于浏览计数等持久计数） */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /** 获取字符串 */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /** 删除单个键 */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /** 批量删除键（一次 DEL 命令，避免循环 N 次网络往返） */
    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    // ==================== 数值操作 ====================

    /** 自增 1 */
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    // ==================== ZSet ====================

    /** 获取 ZSet 指定范围元素（GEO 底层是 ZSet，用于遍历所有成员） */
    public Set<String> zSetRange(String key, long start, long end) {
        return stringRedisTemplate.opsForZSet().range(key, start, end);
    }

    // ==================== GEO ====================

    /** 添加地理位置 */
    public Long geoAdd(String key, double lng, double lat, String member) {
        return stringRedisTemplate.opsForGeo().add(key, new Point(lng, lat), member);
    }

    /**
     * 搜索附近位置（按距离升序）。
     *
     * @param radius 搜索半径（公里）
     */
    public List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoSearch(String key, double lng, double lat, double radius) {
        Circle circle = new Circle(new Point(lng, lat), new Distance(radius, Metrics.KILOMETERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeDistance()
                .includeCoordinates()
                .sortAscending();
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(key, circle, args);
        return results != null ? results.getContent() : Collections.emptyList();
    }

    /** 删除地理位置 */
    public Long geoRemove(String key, String member) {
        return stringRedisTemplate.opsForGeo().remove(key, member);
    }

    /**
     * 检查 member 是否存在于 GEO 集合里。
     * 用 GEOPOS 查坐标：存在返回 Point，不存在返回 null。
     * 相比 geoDistance(m, m) 的方案，能绕开 Redisson 3.23.4 DistanceConvertor 的 NPE bug。
     */
    public boolean geoExists(String key, String member) {
        List<Point> positions = stringRedisTemplate.opsForGeo().position(key, member);
        return positions != null && !positions.isEmpty() && positions.get(0) != null;
    }

    // ==================== 通用 ====================

    /**
     * 使用 SCAN 命令安全地模糊匹配 Key（非阻塞，生产环境推荐）。
     *
     * @param pattern 匹配模式，如 "help:page:*"
     */
    public Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
