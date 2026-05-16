package com.example.demo.service;

import com.example.demo.entity.TNotification;
import org.springframework.data.domain.Page;

/**
 * 通知表 服务类
 */
public interface ITNotificationService {
    Page<TNotification> pageNotifications(Long userId, int pageNum, int pageSize, Boolean readFlag);

    boolean markAsRead(Long userId, Long notificationId);

    boolean deleteNotification(Long userId, Long notificationId);

    void clearNotifications(Long userId);

    int getUnreadCount(Long userId);

    TNotification save(TNotification notification);
}