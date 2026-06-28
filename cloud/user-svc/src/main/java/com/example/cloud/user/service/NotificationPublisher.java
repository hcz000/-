package com.example.cloud.user.service;

import com.example.cloud.common.api.notification.NotificationEvent;
import com.example.cloud.common.api.notification.NotificationType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通知发送辅助类（替代单体里的 NotificationSender）。
 * <p>
 * 任何服务想发通知调这里的方法即可，内部把事件投递到 RabbitMQ 通知交换机，
 * 由 user-svc 的 {@link com.example.cloud.user.mq.NotificationEventListener} 统一处理。
 */
@Slf4j
@Component
public class NotificationPublisher {

    private static final int CONTENT_MAX_LEN = 60;

    @Resource
    private RabbitTemplate rabbitTemplate;

    public void notifyFriendRequestAccepted(Long senderId, Long recipientId, Long requestId) {
        send(senderId, recipientId, NotificationType.FRIEND_REQUEST_ACCEPTED, null,
                requestId == null ? null : String.valueOf(requestId), "你的好友申请已通过");
    }

    public void notifyFriendRequestRejected(Long senderId, Long recipientId, Long requestId) {
        send(senderId, recipientId, NotificationType.FRIEND_REQUEST_REJECTED, null,
                requestId == null ? null : String.valueOf(requestId), "你的好友申请被拒绝");
    }

    public void notifySystem(Long recipientId, Long postId, String relatedId, String content) {
        send(null, recipientId, NotificationType.SYSTEM, postId, relatedId, content);
    }

    private void send(Long senderId, Long recipientId, NotificationType type,
                      Long postId, String relatedId, String content) {
        if (recipientId == null || type == null) return;
        if (senderId != null && senderId.equals(recipientId)) return;  // 不给自己发
        NotificationEvent event = new NotificationEvent(senderId, recipientId, type.getCode(),
                postId, relatedId, shorten(content));
        try {
            rabbitTemplate.convertAndSend(NotificationEvent.EXCHANGE,
                    "notification." + type.name().toLowerCase(),
                    event);
        } catch (Exception e) {
            log.warn("[notification-pub] send failed, recipient={}, type={}", recipientId, type, e);
        }
    }

    private String shorten(String content) {
        if (!StringUtils.hasText(content)) return "";
        String trimmed = content.trim();
        return trimmed.length() <= CONTENT_MAX_LEN ? trimmed : trimmed.substring(0, CONTENT_MAX_LEN);
    }
}
