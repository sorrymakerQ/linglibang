package com.linlibang.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.linlibang.dto.HelpRequestDTO;
import com.linlibang.dto.Result;
import com.linlibang.service.HelpRequestService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * 求助控制器
 * 处理求助发布、搜索、查看等请求
 */
@RestController
@RequestMapping("/help")
public class HelpRequestController {

    @Resource
    private HelpRequestService helpRequestService;

    /**
     * 发布求助 — 需要 help:publish 权限
     *
     * @param dto 求助表单
     */
    @SaCheckPermission("help:publish")
    @PostMapping("/publish")
    public Result publishHelp(@Valid @RequestBody HelpRequestDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        return helpRequestService.publishHelp(dto, userId);
    }

    /**
     * 查询附近的求助
     *
     * @param lng    中心经度
     * @param lat    中心纬度
     * @param radius 搜索半径（公里），默认5
     * @param page   页码，默认1
     * @param size   每页条数，默认10
     */
    @GetMapping("/nearby")
    public Result getNearbyHelp(
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false, defaultValue = "5") Integer radius,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        size = Math.min(size, 50);  // 限制最大每页条数
        return helpRequestService.getNearbyHelp(lng, lat, radius, page, size, categoryId, keyword);
    }

    /**
     * 查询求助详情
     *
     * @param id 求助ID
     */
    @GetMapping("/{id}")
    public Result getHelpById(@PathVariable Long id) {
        return helpRequestService.getHelpById(id);
    }

    /**
     * 搜索求助
     *
     * @param keyword    搜索关键词
     * @param categoryId 分类ID（可选）
     * @param page       页码
     * @param size       每页条数
     */
    @GetMapping("/search")
    public Result searchHelp(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        size = Math.min(size, 50);  // 限制最大每页条数
        return helpRequestService.searchHelp(keyword, categoryId, page, size);
    }

    /**
     * 取消求助 — 需登录
     *
     * @param id 求助ID
     */
    @SaCheckLogin
    @PutMapping("/{id}/cancel")
    public Result cancelHelp(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return helpRequestService.cancelHelp(id, userId);
    }

    /**
     * 我的求助列表 — 需登录
     *
     * @param page 页码
     * @param size 每页条数
     */
    @SaCheckLogin
    @GetMapping("/my")
    public Result getMyHelp(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        size = Math.min(size, 50);  // 限制最大每页条数
        long userId = StpUtil.getLoginIdAsLong();
        return helpRequestService.getMyHelp(userId, page, size);
    }
}
