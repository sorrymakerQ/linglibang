package com.linlibang.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.json.JSONUtil;
import com.linlibang.dto.UserDTO;
import com.linlibang.entity.User;
import com.linlibang.mapper.UserMapper;
import com.linlibang.utils.RedisUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sa-Token 权限扩展——从 Redis 加载用户角色和权限
 * 每次鉴权时触发，优先读 Redis 缓存，未命中则查 DB 并回填缓存
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /** 所有已定义的权限码 */
    private static final List<String> ALL_PERMISSIONS = Arrays.asList(
            "help:publish", "order:accept", "message:send"
    );

    private static final String USER_CACHE_PREFIX = "user:info:";

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisUtils redisUtils;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());

        // 优先从 Redis 读取
        String cachedJson = redisUtils.get(USER_CACHE_PREFIX + userId);
        if (cachedJson != null) {
            UserDTO userDTO = JSONUtil.toBean(cachedJson, UserDTO.class);
            if (userDTO.getRole() != null && userDTO.getRole() == 2) {
                return new ArrayList<>(ALL_PERMISSIONS);
            }
            String permissionsStr = userDTO.getPermissions();
            if (permissionsStr == null || permissionsStr.trim().isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.stream(permissionsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        // Redis 未命中，回退查 DB（兼容缓存过期场景）
        return getPermissionListFromDb(userId);
    }

    private List<String> getPermissionListFromDb(Long userId) {
        User user = userMapper.selectById(userId);
        // 如果用户不存在，返回空权限
        if (user == null) return Collections.emptyList();
        // 用户为管理员 则返回所有权限
        if (user.getRole() != null && user.getRole() == 2) {
            return new ArrayList<>(ALL_PERMISSIONS);
        }
        String permissionsStr = user.getPermissions();
        if (permissionsStr == null || permissionsStr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(permissionsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());

        // 优先从 Redis 读取
        String cachedJson = redisUtils.get(USER_CACHE_PREFIX + userId);
        if (cachedJson != null) {
            UserDTO userDTO = JSONUtil.toBean(cachedJson, UserDTO.class);
            List<String> roles = new ArrayList<>();
            if (userDTO.getRole() != null && userDTO.getRole() == 2) {
                roles.add("admin");
            } else {
                roles.add("user");
            }
            return roles;
        }

        // Redis 未命中，回退查 DB
        return getRoleListFromDb(userId);
    }

    private List<String> getRoleListFromDb(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return Collections.singletonList("user");

        List<String> roles = new ArrayList<>();
        if (user.getRole() != null && user.getRole() == 2) {
            roles.add("admin");
        } else {
            roles.add("user");
        }
        return roles;
    }
}
