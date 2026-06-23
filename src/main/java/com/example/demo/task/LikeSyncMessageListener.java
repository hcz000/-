package com.example.demo.task;

import com.example.demo.repository.PostingsRepository;
import com.example.demo.repository.PrimaryCommentRepository;
import com.example.demo.repository.SecondaryCommentRepository;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 点赞统计同步监听器。
 */
@Slf4j
@Component
public class LikeSyncMessageListener {

    @Resource
    private PostingsRepository postingsRepository;
    @Resource
    private PrimaryCommentRepository primaryCommentRepository;
    @Resource
    private SecondaryCommentRepository secondaryCommentRepository;
    @Resource
    private TransactionTemplate transactionTemplate;

    @RabbitListener(queues = CommentTopics.LIKE_SYNC)
    public void onMessage(String payload,
                         Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!StringUtils.hasText(payload)) {
            // 空消息直接ACK丢弃
            channel.basicAck(deliveryTag, false);
            return;
        }

        LikeSyncMessage message;
        try {
            message = LikeSyncMessage.deserialize(payload);
        } catch (Exception e) {
            log.warn("无效的点赞同步消息载荷: {}", payload, e);
            // 无效消息拒绝并不重新入队
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> dispatch(message));
            // 处理成功,手动ACK
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("点赞同步失败,消息将重新入队: {}", message, e);
            // 处理失败,拒绝并重新入队
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void dispatch(LikeSyncMessage message) {
        int delta = message.getDelta();
        if (delta == 0) {
            return;
        }
        switch (message.getBizType()) {
            case POST -> incrementPostLike(message.getBizId(), delta);
            case PRIMARY_COMMENT -> incrementPrimaryCommentLike(message.getBizId(), delta);
            case SECONDARY_COMMENT -> incrementSecondaryCommentLike(message.getBizId(), delta);
            default -> log.warn("不支持的业务类型 {}", message.getBizType());
        }
    }

    private void incrementPostLike(Long id, int delta) {
        int updated = postingsRepository.incrementLikeCount(id, delta);
        if (updated <= 0) {
            log.warn("帖子 {} 点赞增量同步跳过: 未找到记录", id);
        }
    }

    private void incrementPrimaryCommentLike(Long id, int delta) {
        int updated = primaryCommentRepository.incrementLikeCount(id, delta);
        if (updated <= 0) {
            log.warn("一级评论 {} 点赞增量同步跳过: 未找到记录", id);
        }
    }

    private void incrementSecondaryCommentLike(Long id, int delta) {
        int updated = secondaryCommentRepository.incrementLikeCount(id, delta);
        if (updated <= 0) {
            log.warn("二级评论 {} 点赞增量同步跳过: 未找到记录", id);
        }
    }
}
