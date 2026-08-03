package com.linlibang.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.linlibang.dto.Result;
import com.linlibang.service.OrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 订单控制器
 * 处理接单、取消、完成、评价等请求
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private OrderService orderService;

    /**
     * 接单 — 需要 order:accept 权限
     *
     * @param helpId 求助ID
     */
    @SaCheckPermission("order:accept")
    @PostMapping("/accept/{helpId}")
    public Result acceptOrder(@PathVariable Long helpId) {
        long userId = StpUtil.getLoginIdAsLong();
        return orderService.acceptOrder(helpId, userId);
    }

    /**
     * 取消订单 — 需登录
     *
     * @param id   订单ID
     * @param body 包含取消原因
     */
    @SaCheckLogin
    @PutMapping("/{id}/cancel")
    public Result cancelOrder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        long userId = StpUtil.getLoginIdAsLong();
        String reason = body.getOrDefault("reason", "用户主动取消");
        return orderService.cancelOrder(id, userId, reason);
    }

    /**
     * 确认完成订单 — 需登录
     *
     * @param id 订单ID
     */
    @SaCheckLogin
    @PutMapping("/{id}/finish")
    public Result finishOrder(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return orderService.finishOrder(id, userId);
    }

    /**
     * 评价订单 — 需登录
     *
     * @param id   订单ID
     * @param body 包含评分和评价内容 { score: 5, comment: "很好" }
     */
    @SaCheckLogin
    @PutMapping("/{id}/review")
    public Result reviewOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        long userId = StpUtil.getLoginIdAsLong();
        Object scoreObj = body.get("score");
        if (scoreObj == null) {
            return Result.fail("评分不能为空");
        }
        Integer score = scoreObj instanceof Integer ? (Integer) scoreObj : Integer.valueOf(scoreObj.toString());
        if (score < 1 || score > 5) {
            return Result.fail("评分范围为1-5分");
        }
        String comment = body.get("comment") != null ? body.get("comment").toString() : null;
        return orderService.reviewOrder(id, userId, score, comment);
    }

    /**
     * 查询订单详情 — 需登录
     *
     * @param id 订单ID
     */
    @SaCheckLogin
    @GetMapping("/{id}")
    public Result getOrderById(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return orderService.getOrderById(id, userId);
    }

    /**
     * 查询我的订单 — 需登录
     *
     * @param role 角色：publisher-发布的，helper-接单的，all-全部
     * @param page 页码
     * @param size 每页条数
     */
    @SaCheckLogin
    @GetMapping("/my")
    public Result getMyOrders(
            @RequestParam(required = false, defaultValue = "all") String role,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        size = Math.min(size, 50);  // 限制最大每页条数
        long userId = StpUtil.getLoginIdAsLong();
        return orderService.getMyOrders(userId, role, page, size);
    }
}
