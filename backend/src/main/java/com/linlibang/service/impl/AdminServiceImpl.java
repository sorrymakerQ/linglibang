package com.linlibang.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.linlibang.dto.Result;
import com.linlibang.entity.HelpRequest;
import com.linlibang.entity.User;
import com.linlibang.mapper.HelpRequestMapper;
import com.linlibang.mapper.OrderMapper;
import com.linlibang.mapper.UserMapper;
import com.linlibang.service.AdminService;
import com.linlibang.utils.RedisUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private HelpRequestMapper helpRequestMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private RedisUtils redisUtils;

    @Override
    public Result getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userMapper.selectCount());
        stats.put("helpCount", helpRequestMapper.selectCount(null));
        stats.put("pendingHelpCount", helpRequestMapper.selectCountByStatus(1));
        stats.put("finishedOrderCount", orderMapper.selectCountByStatus(3));
        return Result.ok(stats);
    }

    @Override
    public Result getUserList(Integer page, Integer size) {
        size = Math.min(size, 50);
        int offset = (page - 1) * size;
        List<User> list = userMapper.selectPage(offset, size);
        // 安全：管理员列表不返回密码哈希（避免通过 Network 面板泄露 BCrypt 值）
        list.forEach(u -> u.setPassword(null));
        Long total = userMapper.selectCount();
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("list", list);
        resultMap.put("total", total);
        return Result.ok(resultMap);
    }

    @Override
    public Result updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) return Result.fail("用户不存在");
        if (user.getRole() != null && user.getRole() == 2) {
            return Result.fail("不能禁用管理员账号");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        if (status == 0) StpUtil.logout(id);
        return Result.ok(status == 1 ? "用户已启用" : "用户已禁用");
    }

    @Override
    public Result updateUserPermissions(Long id, String permissions) {
        User user = userMapper.selectById(id);
        if (user == null) return Result.fail("用户不存在");
        user.setPermissions(permissions);
        userMapper.updateById(user);
        // 清除 Redis 缓存 + 强制重新登录，使新权限立即生效
        redisUtils.delete("user:info:" + id);
        StpUtil.logout(id);
        return Result.ok("权限已更新");
    }

    @Override
    public Result getHelpList(Integer page, Integer size, Integer status) {
        size = Math.min(size, 50);
        int offset = (page - 1) * size;
        List<HelpRequest> list = helpRequestMapper.selectPageAll(offset, size, status);
        Long total = helpRequestMapper.selectCountAll(status);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("list", list);
        resultMap.put("total", total);
        return Result.ok(resultMap);
    }

    @Override
    public Result deleteHelp(Long id) {
        HelpRequest help = helpRequestMapper.selectById(id);
        if (help == null) return Result.fail("求助不存在");
        help.setIsDeleted(1);
        helpRequestMapper.updateById(help);

        // 清理 Redis 缓存和 GEO 位置（修复：之前只更新 DB，缓存中仍存在已删除数据）
        redisUtils.delete("help:item:" + id);
        redisUtils.geoRemove("help:location", id.toString());
        // 清除分页缓存
        java.util.Set<String> pageKeys = redisUtils.scanKeys("help:page:*");
        if (pageKeys != null) {
            for (String key : pageKeys) {
                redisUtils.delete(key);
            }
        }

        return Result.ok("求助已删除");
    }
}
