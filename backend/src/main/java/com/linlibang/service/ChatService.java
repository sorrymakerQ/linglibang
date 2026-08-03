package com.linlibang.service;

import com.linlibang.dto.Result;

public interface ChatService {
    Result getOrderMessages(Long orderId, Long userId);
    Result getHelpMessages(Long helpId, Long userId);
    Result sendMessage(Long senderId, Long orderId, Long helpId, String content);
    Result getUnreadCount(Long userId);
    Result getConversations(Long userId);
    Result getHelpChatInfo(Long helpId, Long userId);
}
