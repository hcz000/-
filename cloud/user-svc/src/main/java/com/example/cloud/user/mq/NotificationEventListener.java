package com.example.cloud.user.mq;

import com.example.cloud.common.api.notification.NotificationEvent;
import com.example.cloud.user.config.RabbitMQConfig;
import com.example.cloud.user.entity.TNotification;
import com.example.cloud.user.service.ITNotificationService;
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
 * 这里统一消费、落库；后续可以接 WebSocket 推送实时通知。
 */
@Slf4j
@Component
public class NotificationEventListener {

    @Resource
    private ITNotificationService notificationService;

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
            notificationService.save(row);
            log.info("[notification-mq] saved notification recipient={}, type={}",
                    event.getRecipientId(), event.getType());
            // TODO WebSocket 实时推送（chat 模块迁入后接通）
        } catch (Exception e) {
            log.error("[notification-mq] save notification failed: {}", event, e);
            throw new RuntimeException(e); // 让 RabbitMQ 重试
        }
    }
}
