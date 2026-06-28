package com.example.cloud.user.service;

import com.example.cloud.user.entity.TNotification;
import org.springframework.data.domain.Page;

/**
 * 通知服务。
 */
public interface ITNotificationService {

    Page<TNotification> pageNotifications(Long userId, int pageNum, int pageSize, Boolean readFlag);

    boolean markAsRead(Long userId, Long notificationId);

    boolean deleteNotification(Long userId, Long notificationId);

    void clearNotifications(Long userId);

    int getUnreadCount(Long userId);

    TNotification save(TNotification notification);
}
