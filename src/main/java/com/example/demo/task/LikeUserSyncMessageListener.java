package com.example.demo.task;

import com.example.demo.service.LikeUserSyncService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Slf4j
@Component
public class LikeUserSyncMessageListener {

    @Resource
    private LikeUserSyncService likeUserSyncService;

    @RabbitListener(queues = CommentTopics.LIKE_USER_SYNC)
    public void onMessage(String payload,
                         Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!StringUtils.hasText(payload)) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        LikeUserSyncMessage message;
        try {
            message = LikeUserSyncMessage.deserialize(payload);
        } catch (Exception e) {
            log.warn("无效的用户点赞同步消息载荷: {}", payload, e);
            // 无效消息拒绝并不重新入队
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        try {
            likeUserSyncService.applyMessage(message);
            // 处理成功,手动ACK
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("用户点赞同步失败,消息将重新入队: {}", message, e);
            // 处理失败,重新入队
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
