package com.linlibang.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.linlibang.service.ChatService;
import com.linlibang.dto.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    /** 获取订单聊天消息 — 需登录 */
    @SaCheckLogin
    @GetMapping("/order/{orderId}")
    public Result getOrderMessages(@PathVariable Long orderId) {
        return chatService.getOrderMessages(orderId, StpUtil.getLoginIdAsLong());
    }

    /** 获取求助聊天消息 — 需登录 */
    @SaCheckLogin
    @GetMapping("/help/{helpId}")
    public Result getHelpMessages(@PathVariable Long helpId) {
        return chatService.getHelpMessages(helpId, StpUtil.getLoginIdAsLong());
    }

    /**
     * 发送消息 — 需要 message:send 权限
     */
    @SaCheckPermission("message:send")
    @PostMapping("/send")
    public Result sendMessage(@RequestBody Map<String, Object> body) {
        long senderId = StpUtil.getLoginIdAsLong();
        Long orderId = body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : null;
        Long helpId = body.get("helpId") != null ? Long.valueOf(body.get("helpId").toString()) : null;
        String content = body.get("content") != null ? body.get("content").toString() : "";
        return chatService.sendMessage(senderId, orderId, helpId, content);
    }

    /** 未读消息数 — 需登录 */
    @SaCheckLogin
    @GetMapping("/unread")
    public Result getUnreadCount() {
        return chatService.getUnreadCount(StpUtil.getLoginIdAsLong());
    }

    /** 会话列表 — 需登录 */
    @SaCheckLogin
    @GetMapping("/conversations")
    public Result getConversations() {
        return chatService.getConversations(StpUtil.getLoginIdAsLong());
    }

    /**
     * 获取求助的聊天头部信息（对方昵称、求助标题等） — 需登录
     */
    @SaCheckLogin
    @GetMapping("/help/{helpId}/info")
    public Result getHelpChatInfo(@PathVariable Long helpId) {
        return chatService.getHelpChatInfo(helpId, StpUtil.getLoginIdAsLong());
    }
}
