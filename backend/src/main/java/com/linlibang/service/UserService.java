package com.linlibang.service;

import com.linlibang.dto.LoginFormDTO;
import com.linlibang.dto.RegisterFormDTO;
import com.linlibang.dto.Result;
import com.linlibang.dto.UserDTO;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户登录
     *
     * @param loginForm 登录表单（手机号 + 密码）
     * @return 登录结果，包含 JWT Token
     */
    Result login(LoginFormDTO loginForm);

    /**
     * 用户注册
     *
     * @param registerForm 注册表单
     * @return 注册结果
     */
    Result register(RegisterFormDTO registerForm);

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息DTO
     */
    Result getCurrentUser();

    /**
     * 根据ID查询用户信息
     *
     * @param userId 用户ID
     * @return 用户信息DTO
     */
    Result getUserById(Long userId);

    /**
     * 更新用户信息
     *
     * @param userDTO 用户信息
     * @return 更新结果
     */
    Result updateUser(UserDTO userDTO);

}
