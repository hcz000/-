package com.example.demo.task;

import com.example.demo.repository.PostingsRepository;
import com.example.demo.repository.PrimaryCommentRepository;
import com.example.demo.repository.SecondaryCommentRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

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
    public void onMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            return;
        }

        LikeSyncMessage message;
        try {
            message = LikeSyncMessage.deserialize(payload);
        } catch (Exception e) {
            log.warn("无效的点赞同步消息载荷: {}", payload, e);
            throw new AmqpRejectAndDontRequeueException("invalid like-sync payload", e);
        }

        transactionTemplate.executeWithoutResult(status -> dispatch(message));
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
