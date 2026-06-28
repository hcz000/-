package com.example.cloud.user.mq;

import com.example.cloud.common.api.notification.NotificationEvent;
import com.example.cloud.user.dto.NotificationResponse;
import com.example.cloud.user.entity.TNotification;
import com.example.cloud.user.service.ITNotificationService;
import com.example.cloud.user.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通知事件消费者。
 * <p>
 * 任意服务（user-svc 自己 / post-svc / push-svc 等）通过
 * topic exchange {@code notification.events} 发出 {@link NotificationEvent}，
 * 这里统一消费、落库、推 WebSocket 实时通知。
 */
@Slf4j
@Component
public class NotificationEventListener {

    @Resource
    private ITNotificationService notificationService;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @RabbitListener(queues = "#{notificationQueue.name}")
    public void onNotification(NotificationEvent event) {
        if (event == null || event.getRecipientId() == null || event.getType() == null) {
            log.warn("[notification-mq] dropped invalid event: {}", event);
            return;
        }
        try {
            TNotification row = new TNotification();
            row.setSenderId(event.getSenderId());
            row.setRecipientId(event.getRecipientId());
            row.setType(event.getType());
            row.setPostId(event.getPostId());
            row.setRelatedId(event.getRelatedId());
            row.setContent(event.getContent());
            row.setCreateTime(LocalDateTime.now());
            row.setReadFlag(false);
            TNotification saved = notificationService.save(row);
            log.info("[notification-mq] saved notification recipient={}, type={}",
                    event.getRecipientId(), event.getType());

            // 通过 WebSocket 给在线的接收方推实时通知（不在线则忽略，下次登录走 HTTP 列表）
            try {
                chatWebSocketHandler.broadcastNotification(
                        event.getRecipientId(), NotificationResponse.fromEntity(saved));
            } catch (Exception e) {
                log.warn("[notification-mq] WebSocket broadcast failed, recipient={}",
                        event.getRecipientId(), e);
            }
        } catch (Exception e) {
            log.error("[notification-mq] save notification failed: {}", event, e);
            throw new RuntimeException(e);
        }
    }
}

