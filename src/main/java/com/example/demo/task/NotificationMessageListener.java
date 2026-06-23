package com.example.demo.task;

import com.example.demo.entity.TNotification;
import com.example.demo.entity.dto.NotificationResponse;
import com.example.demo.service.ITNotificationService;
import com.example.demo.websocket.ChatWebSocketHandler;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
public class NotificationMessageListener {

    @Resource
    private ITNotificationService notificationService;
    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @RabbitListener(queues = CommentTopics.NOTIFICATION)
    public void onMessage(String payload,
                         Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!StringUtils.hasText(payload)) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        NotificationMessage message;
        try {
            message = NotificationMessage.deserialize(payload);
        } catch (Exception e) {
            log.warn("无效的通知载荷：{}", payload, e);
            // 无效消息不重新入队
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        if (message.getRecipientId() == null) {
            log.warn("忽略没有接收者的通知：{}", payload);
            channel.basicAck(deliveryTag, false);
            return;
        }

        TNotification notification = new TNotification();
        notification.setSenderId(message.getSenderId());
        notification.setRecipientId(message.getRecipientId());
        notification.setRelatedId(message.getRelatedId());
        notification.setPostId(message.getPostId());
        notification.setType(message.getType());
        notification.setContent(message.getContent());
        notification.setCreateTime(LocalDateTime.now());
        notification.setReadFlag(false);

        try {
            notificationService.save(notification);
        } catch (Exception e) {
            log.error("为接收者 {} 保存通知失败,消息将重新入队", message.getRecipientId(), e);
            // 数据库保存失败,重新入队
            channel.basicNack(deliveryTag, false, true);
            return;
        }

        // WebSocket推送失败不影响消息ACK
        NotificationResponse response = NotificationResponse.fromEntity(notification);
        try {
            chatWebSocketHandler.broadcastNotification(message.getRecipientId(), response);
        } catch (Exception e) {
            log.warn("为接收者 {} 推送通知到 WebSocket 失败", message.getRecipientId(), e);
        }
        
        // 处理成功,手动ACK
        channel.basicAck(deliveryTag, false);
    }
}
