package com.linlibang.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.linlibang.dto.LoginFormDTO;
import com.linlibang.dto.RegisterFormDTO;
import com.linlibang.dto.Result;
import com.linlibang.dto.UserDTO;
import com.linlibang.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * 用户控制器
 * 处理用户登录、注册、个人信息等请求
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户登录 — 公开
     *
     * @param loginForm 登录表单
     * @return Token 和用户信息
     */
    @PostMapping("/login")
    public Result login(@Valid @RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /**
     * 用户注册 — 公开
     *
     * @param registerForm 注册表单
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result register(@Valid @RequestBody RegisterFormDTO registerForm) {
        return userService.register(registerForm);
    }

    /**
     * 获取当前登录用户信息 — 需登录
     */
    @SaCheckLogin
    @GetMapping("/me")
    public Result getCurrentUser() {
        return userService.getCurrentUser();
    }

    /**
     * 查看其他用户信息 — 公开
     *
     * @param id 用户ID
     */
    @GetMapping("/{id}")
    public Result getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    /**
     * 更新个人信息 — 需登录
     *
     * @param userDTO 用户信息
     */
    @SaCheckLogin
    @PutMapping("/update")
    public Result updateUser(@RequestBody UserDTO userDTO) {
        return userService.updateUser(userDTO);
    }

}
