package com.linlibang.service;

import com.linlibang.dto.Result;

public interface NotificationService {
    Result getNotifications(Long userId, Integer page, Integer size);
    Result readNotification(Long id, Long userId);
    Result getUnreadCount(Long userId);
    Result readAll(Long userId);
}
