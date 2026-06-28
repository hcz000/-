package com.example.cloud.post.mq;

import com.example.cloud.post.constant.CommentTopics;
import com.example.cloud.post.dto.LikeSyncMessage;
import com.example.cloud.post.repository.PostingsRepository;
import com.example.cloud.post.repository.PrimaryCommentRepository;
import com.example.cloud.post.repository.SecondaryCommentRepository;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 点赞计数同步监听器：消费 LikeBufferTrigger 聚合后的消息，increment 数据库的 like_count 字段。
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
    public void onMessage(String payload, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!StringUtils.hasText(payload)) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        LikeSyncMessage message;
        try {
            message = LikeSyncMessage.deserialize(payload);
        } catch (Exception e) {
            log.warn("无效的点赞同步消息载荷: {}", payload, e);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> dispatch(message));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("点赞同步失败,消息将重新入队: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void dispatch(LikeSyncMessage message) {
        int delta = message.getDelta();
        if (delta == 0) return;
        switch (message.getBizType()) {
            case POST -> postingsRepository.incrementLikeCount(message.getBizId(), delta);
            case PRIMARY_COMMENT -> primaryCommentRepository.incrementLikeCount(message.getBizId(), delta);
            case SECONDARY_COMMENT -> secondaryCommentRepository.incrementLikeCount(message.getBizId(), delta);
            default -> log.warn("不支持的业务类型 {}", message.getBizType());
        }
    }
}
