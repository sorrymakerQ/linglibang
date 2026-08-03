package com.linlibang.service.impl;

import com.linlibang.dto.Result;
import com.linlibang.entity.ChatMessage;
import com.linlibang.entity.HelpRequest;
import com.linlibang.entity.Order;
import com.linlibang.entity.User;
import com.linlibang.mapper.ChatMessageMapper;
import com.linlibang.mapper.HelpRequestMapper;
import com.linlibang.mapper.OrderMapper;
import com.linlibang.mapper.UserMapper;
import com.linlibang.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private HelpRequestMapper helpRequestMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public Result getOrderMessages(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) return Result.fail("订单不存在");
        if (!order.getPublisherId().equals(userId) && !order.getHelperId().equals(userId))
            return Result.fail("无权查看");
        chatMessageMapper.markRead(orderId, null, userId);
        return Result.ok(chatMessageMapper.selectByOrderId(orderId));
    }

    @Override
    public Result getHelpMessages(Long helpId, Long userId) {
        HelpRequest help = helpRequestMapper.selectById(helpId);
        if (help == null) return Result.fail("求助不存在");
        chatMessageMapper.markRead(null, helpId, userId);
        if (help.getUserId().equals(userId)) {
            return Result.ok(chatMessageMapper.selectByHelpId(helpId));
        }
        return Result.ok(chatMessageMapper.selectByHelpIdAndUser(helpId, userId));
    }

    @Override
    public Result sendMessage(Long senderId, Long orderId, Long helpId, String content) {
        if (content == null || content.trim().isEmpty()) return Result.fail("消息不能为空");
        content = content.trim();

        Long receiverId;
        if (orderId != null) {
            Order order = orderMapper.selectById(orderId);
            if (order == null) return Result.fail("订单不存在");
            if (!order.getPublisherId().equals(senderId) && !order.getHelperId().equals(senderId))
                return Result.fail("无权发送消息");
            receiverId = order.getPublisherId().equals(senderId) ? order.getHelperId() : order.getPublisherId();
        } else if (helpId != null) {
            HelpRequest help = helpRequestMapper.selectById(helpId);
            if (help == null) return Result.fail("求助不存在");
            if (help.getUserId().equals(senderId)) {
                List<ChatMessage> history = chatMessageMapper.selectByHelpId(helpId);
                receiverId = null;
                for (ChatMessage h : history) {
                    if (!h.getSenderId().equals(senderId)) { receiverId = h.getSenderId(); break; }
                    if (!h.getReceiverId().equals(senderId)) { receiverId = h.getReceiverId(); break; }
                }
                if (receiverId == null) return Result.fail("暂无联系人可回复");
            } else {
                receiverId = help.getUserId();
            }
        } else {
            return Result.fail("缺少 orderId 或 helpId");
        }

        ChatMessage msg = new ChatMessage();
        msg.setOrderId(orderId);
        msg.setHelpId(helpId);
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        chatMessageMapper.insert(msg);

        try {
            messagingTemplate.convertAndSendToUser(receiverId.toString(), "/queue/chat", msg);
        } catch (Exception e) {
            // WebSocket 推送失败不阻断主流程（消息已入库），仅记录
            log.warn("WebSocket 推送失败, receiverId={}, msgId={}: {}", receiverId, msg.getId(), e.getMessage());
        }

        return Result.ok(msg);
    }

    @Override
    public Result getUnreadCount(Long userId) {
        return Result.ok(chatMessageMapper.countUnread(userId));
    }

    @Override
    public Result getConversations(Long userId) {
        List<ChatMessage> list = chatMessageMapper.selectConversations(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        if (list.isEmpty()) return Result.ok(result);

        // 批量查询关联数据（避免 N+1 问题）
        Set<Long> otherUserIds = new HashSet<>();
        Set<Long> helpIds = new HashSet<>();
        for (ChatMessage m : list) {
            otherUserIds.add(m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId());
            if (m.getHelpId() != null) helpIds.add(m.getHelpId());
        }
        Map<Long, User> userMap = userMapper.selectByIds(new ArrayList<>(otherUserIds)).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, HelpRequest> helpMap = helpIds.isEmpty() ? Collections.emptyMap() :
                helpRequestMapper.selectByIds(new ArrayList<>(helpIds)).stream()
                        .collect(java.util.stream.Collectors.toMap(HelpRequest::getId, h -> h, (a, b) -> a));

        for (ChatMessage m : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("helpId", m.getHelpId());
            item.put("orderId", m.getOrderId());
            item.put("content", m.getContent());
            item.put("senderId", m.getSenderId());
            item.put("receiverId", m.getReceiverId());
            item.put("isRead", m.getIsRead());
            item.put("createTime", m.getCreateTime());
            Long otherId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            User other = userMap.get(otherId);
            if (other != null) {
                item.put("otherName", other.getNickname());
                item.put("otherAvatar", other.getAvatar());
            }
            if (m.getHelpId() != null) {
                HelpRequest help = helpMap.get(m.getHelpId());
                if (help != null) item.put("helpTitle", help.getTitle());
            }
            result.add(item);
        }
        return Result.ok(result);
    }

    @Override
    public Result getHelpChatInfo(Long helpId, Long userId) {
        HelpRequest help = helpRequestMapper.selectById(helpId);
        if (help == null) return Result.fail("求助不存在");

        Map<String, Object> info = new HashMap<>();
        info.put("helpId", help.getId());
        info.put("helpTitle", help.getTitle());
        info.put("helpStatus", help.getStatus());

        // 如果当前用户是发布者，对方是第一个给他发私信的人
        // 如果不是发布者，对方就是发布者
        Long otherId;
        if (help.getUserId().equals(userId)) {
            // 发布者：从已有消息中找对方
            List<ChatMessage> history = chatMessageMapper.selectByHelpId(helpId);
            otherId = null;
            for (ChatMessage h : history) {
                if (!h.getSenderId().equals(userId)) { otherId = h.getSenderId(); break; }
                if (!h.getReceiverId().equals(userId)) { otherId = h.getReceiverId(); break; }
            }
        } else {
            otherId = help.getUserId();
        }

        if (otherId != null) {
            User other = userMapper.selectById(otherId);
            if (other != null) {
                info.put("otherName", other.getNickname());
                info.put("otherAvatar", other.getAvatar());
            }
        }
        return Result.ok(info);
    }
}
