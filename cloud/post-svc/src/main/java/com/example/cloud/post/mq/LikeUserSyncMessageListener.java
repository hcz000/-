package com.example.cloud.post.mq;

import com.example.cloud.post.constant.CommentTopics;
import com.example.cloud.post.dto.LikeUserSyncMessage;
import com.example.cloud.post.service.LikeUserSyncService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 用户点赞状态同步监听器：消费 LikeBufferTrigger 推出的用户态消息，
 * 写 user_like 表 + 触发兴趣向量更新。
 */
@Slf4j
@Component
public class LikeUserSyncMessageListener {

    @Resource
    private LikeUserSyncService likeUserSyncService;

    @RabbitListener(queues = CommentTopics.LIKE_USER_SYNC)
    public void onMessage(String payload, Channel channel,
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
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            likeUserSyncService.applyMessage(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("用户点赞同步失败,消息将重新入队: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
