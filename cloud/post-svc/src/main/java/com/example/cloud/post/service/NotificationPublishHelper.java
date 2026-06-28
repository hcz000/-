package com.example.cloud.post.service;

import com.example.cloud.common.api.notification.NotificationEvent;
import com.example.cloud.common.api.notification.NotificationType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * post-svc 端的通知发布辅助（发到统一的 notification.events 交换机，
 * 由 user-svc 的 NotificationEventListener 消费落库）。
 */
@Slf4j
@Component
public class NotificationPublishHelper {

    private static final int CONTENT_MAX_LEN = 60;

    @Resource(name = "rabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    public void notifyAuditApproved(Long recipientId, Long postId, String title) {
        send(null, recipientId, NotificationType.SYSTEM, postId,
                "/posts/" + postId,
                "你的帖子《" + shorten(title) + "》已审核通过");
    }

    public void notifyAuditRejected(Long recipientId, Long postId, String title) {
        send(null, recipientId, NotificationType.SYSTEM, postId, null,
                "你的帖子《" + shorten(title) + "》审核未通过");
    }

    private void send(Long senderId, Long recipientId, NotificationType type,
                      Long postId, String relatedId, String content) {
        if (recipientId == null || type == null) return;
        if (senderId != null && senderId.equals(recipientId)) return;
        NotificationEvent event = new NotificationEvent(senderId, recipientId, type.getCode(),
                postId, relatedId, shorten(content));
        try {
            rabbitTemplate.convertAndSend(NotificationEvent.EXCHANGE,
                    "notification." + type.name().toLowerCase(),
                    event);
        } catch (Exception e) {
            log.warn("[notification-pub] post-svc → notification failed, recipient={}", recipientId, e);
        }
    }

    private String shorten(String content) {
        if (!StringUtils.hasText(content)) return "";
        String trimmed = content.trim();
        return trimmed.length() <= CONTENT_MAX_LEN ? trimmed : trimmed.substring(0, CONTENT_MAX_LEN);
    }
}
