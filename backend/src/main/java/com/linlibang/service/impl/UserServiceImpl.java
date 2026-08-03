package com.linlibang.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.linlibang.mapper.UserMapper;
import com.linlibang.dto.LoginFormDTO;
import com.linlibang.dto.RegisterFormDTO;
import com.linlibang.dto.Result;
import com.linlibang.dto.UserDTO;
import com.linlibang.entity.User;
import com.linlibang.service.UserService;
import com.linlibang.utils.RedisUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 * 使用 JdbcTemplate DAO 进行数据库操作
 */
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private RedisUtils redisUtils;

    /** Redis缓存前缀 */
    private static final String USER_CACHE_PREFIX = "user:info:";

    /** 用户信息缓存时间（分钟） */
    private static final long USER_CACHE_TTL = 30;

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1. 校验手机号和密码
        String phone = loginForm.getPhone();
        String password = loginForm.getPassword();

        // 2. 根据手机号查询用户（使用 SQL 语句：SELECT * FROM tb_user WHERE phone = ?）
        User user = userMapper.selectByPhone(phone);

        // 3. 用户不存在
        if (user == null) {
            return Result.fail("手机号未注册，请先注册");
        }

        // 4. 用户被禁用
        if (user.getStatus() == 0) {
            return Result.fail("账号已被禁用，请联系管理员");
        }

        // 5. 密码校验
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return Result.fail("密码错误");
        }

        // 6. Sa-Token 登录（自动生成 Token）
        StpUtil.login(user.getId());

        // 7. 获取 Token 值
        String token = StpUtil.getTokenValue();

        // 8. 缓存用户信息到 Redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        redisUtils.set(USER_CACHE_PREFIX + user.getId(),
                JSONUtil.toJsonStr(userDTO),
                USER_CACHE_TTL, TimeUnit.MINUTES);

        // 8. 返回 Token
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("token", token);
        resultMap.put("userInfo", userDTO);

        return Result.ok("登录成功", resultMap);
    }

    @Override
    @Transactional
    public Result register(RegisterFormDTO registerForm) {
        // 1. 校验手机号是否已注册（使用 SQL 语句：SELECT * FROM tb_user WHERE phone = ?）
        String phone = registerForm.getPhone();
        User existUser = userMapper.selectByPhone(phone);
        if (existUser != null) {
            return Result.fail("该手机号已注册");
        }

        // 2. 创建用户对象
        User user = new User();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(registerForm.getPassword()));
        // 设置默认昵称
        user.setNickname(registerForm.getNickname() != null
                ? registerForm.getNickname()
                : "邻居" + phone.substring(7));
        user.setCredit(100);  // 初始信用分
        user.setStatus(1);    // 正常状态
        user.setRole(1);      // 普通用户
        user.setPermissions("help:publish,order:accept,message:send");  // 默认权限码

        // 3. 保存到数据库（使用 SQL 语句：INSERT INTO tb_user (...) VALUES (...)）
        userMapper.insert(user);

        return Result.ok("注册成功");
    }

    @Override
    public Result getCurrentUser() {
        // 1. 从 Sa-Token 获取当前登录用户ID
        long userId = StpUtil.getLoginIdAsLong();

        // 2. 先从 Redis 查缓存
        String cachedJson = redisUtils.get(USER_CACHE_PREFIX + userId);
        if (cachedJson != null) {
            UserDTO userDTO = cn.hutool.json.JSONUtil.toBean(cachedJson, UserDTO.class);
            return Result.ok(userDTO);
        }

        // 3. 缓存未命中，查数据库（使用 SQL 语句：SELECT * FROM tb_user WHERE id = ?）
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 4. 写入缓存
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        redisUtils.set(USER_CACHE_PREFIX + userId,
                cn.hutool.json.JSONUtil.toJsonStr(userDTO),
                USER_CACHE_TTL, TimeUnit.MINUTES);

        return Result.ok(userDTO);
    }

    @Override
    public Result getUserById(Long userId) {
        UserDTO userDTO;

        // 1. 先从 Redis 查缓存（缓存完整信息，供 getCurrentUser 自身使用）
        String cachedJson = redisUtils.get(USER_CACHE_PREFIX + userId);
        if (cachedJson != null) {
            userDTO = cn.hutool.json.JSONUtil.toBean(cachedJson, UserDTO.class);
        } else {
            // 2. 查数据库（使用 SQL 语句：SELECT * FROM tb_user WHERE id = ?）
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.fail("用户不存在");
            }
            // 3. 写入缓存（修复：getUserById 查询后未缓存，导致每次请求都穿透到 DB）
            userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            redisUtils.set(USER_CACHE_PREFIX + userId,
                    cn.hutool.json.JSONUtil.toJsonStr(userDTO),
                    USER_CACHE_TTL, TimeUnit.MINUTES);
        }

        // 4. 返回公开信息（脱敏：手机号掩码，剥离精确经纬度和角色，避免公开接口泄露隐私/枚举管理员）
        return Result.ok(toPublicDTO(userDTO));
    }

    /**
     * 转为对外公开的 UserDTO：手机号脱敏，剥离精确经纬度和角色
     */
    private UserDTO toPublicDTO(UserDTO src) {
        UserDTO pub = BeanUtil.copyProperties(src, UserDTO.class);
        pub.setPhone(maskPhone(src.getPhone()));
        pub.setLng(null);
        pub.setLat(null);
        pub.setRole(null);
        return pub;
    }

    /** 手机号脱敏：138****1234 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    @Override
    @Transactional
    public Result updateUser(UserDTO userDTO) {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 更新允许修改的字段
        if (userDTO.getNickname() != null) {
            user.setNickname(userDTO.getNickname());
        }
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
        }
        if (userDTO.getGender() != null) {
            user.setGender(userDTO.getGender());
        }
        if (userDTO.getCommunity() != null) {
            user.setCommunity(userDTO.getCommunity());
        }
        if (userDTO.getIntro() != null) {
            user.setIntro(userDTO.getIntro());
        }

        // 使用 SQL 语句：UPDATE tb_user SET ... WHERE id = ?
        userMapper.updateById(user);

        // 删除缓存
        redisUtils.delete(USER_CACHE_PREFIX + userId);

        return Result.ok("个人信息更新成功");
    }

}
