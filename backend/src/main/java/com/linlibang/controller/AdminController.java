package com.linlibang.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.linlibang.service.AdminService;
import com.linlibang.dto.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Resource
    private AdminService adminService;

    @SaCheckRole("admin")
    @GetMapping("/stats")
    public Result getStats() {
        return adminService.getStats();
    }

    @SaCheckRole("admin")
    @GetMapping("/users")
    public Result getUserList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return adminService.getUserList(page, size);
    }

    @SaCheckRole("admin")
    @PutMapping("/user/{id}/status")
    public Result updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        return adminService.updateUserStatus(id, status);
    }

    /** 修改用户权限码 — 管理员操作 */
    @SaCheckRole("admin")
    @PutMapping("/user/{id}/permissions")
    public Result updateUserPermissions(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String permissions = body.get("permissions");
        if (permissions == null || permissions.trim().isEmpty()) {
            return Result.fail("权限码不能为空");
        }
        return adminService.updateUserPermissions(id, permissions.trim());
    }

    @SaCheckRole("admin")
    @GetMapping("/helps")
    public Result getHelpList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        return adminService.getHelpList(page, size, status);
    }

    @SaCheckRole("admin")
    @DeleteMapping("/help/{id}")
    public Result deleteHelp(@PathVariable Long id) {
        return adminService.deleteHelp(id);
    }
}
