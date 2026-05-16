package com.example.demo.task;

import com.example.demo.service.LikeUserSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class LikeUserSyncMessageListener {

    @Resource
    private LikeUserSyncService likeUserSyncService;

    @RabbitListener(queues = CommentTopics.LIKE_USER_SYNC)
    public void onMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            return;
        }

        LikeUserSyncMessage message;
        try {
            message = LikeUserSyncMessage.deserialize(payload);
        } catch (Exception e) {
            log.warn("无效的用户点赞同步消息载荷: {}", payload, e);
            throw new AmqpRejectAndDontRequeueException("invalid like-user-sync payload", e);
        }

        likeUserSyncService.applyMessage(message);
    }
}
