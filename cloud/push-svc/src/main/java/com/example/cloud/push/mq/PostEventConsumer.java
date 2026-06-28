package com.example.cloud.push.mq;

import com.example.cloud.common.api.post.PostCreatedEvent;
import com.example.cloud.push.config.RabbitMQConfig;
import com.example.cloud.push.entity.Postings;
import com.example.cloud.push.repository.PostingsRepository;
import com.example.cloud.push.service.CandidatePoolService;
import com.example.cloud.push.service.HotRankService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 消费 post-svc 投递的「帖子创建」事件，把帖子加入推送候选池。
 *
 * <h3>幂等性保证</h3>
 * RabbitMQ 默认 at-least-once 投递，同一条消息可能被消费多次（重启 / ACK 丢失等）。
 * 这里用 Redis SETNX 做幂等去重：
 * <pre>
 *   key = push:event:seen:{eventId}, TTL 7 天
 *   首次设置成功 → 处理
 *   设置失败（已存在） → 跳过，但仍然 ACK（已处理过）
 * </pre>
 *
 * <h3>失败重试</h3>
 * 业务抛异常会导致 nack + requeue，消息回到队列重新投递。
 * 实际生产建议配死信队列（DLX）防止毒消息无限循环，本 demo 简化。
 */
@Slf4j
@Component
public class PostEventConsumer {

    private static final String SEEN_KEY_PREFIX = "push:event:seen:";
    private static final long SEEN_TTL_DAYS = 7;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private CandidatePoolService candidatePoolService;

    @Resource
    private HotRankService hotRankService;

    @RabbitListener(queues = RabbitMQConfig.PUSH_POST_CREATED_QUEUE)
    public void onPostCreated(PostCreatedEvent event) {
        if (event == null || event.getEventId() == null || event.getPostId() == null) {
            log.warn("[push-mq] dropped invalid event: {}", event);
            return;
        }

        // 1. 幂等去重
        Boolean firstTime = stringRedisTemplate.opsForValue()
                .setIfAbsent(SEEN_KEY_PREFIX + event.getEventId(),
                        String.valueOf(System.currentTimeMillis()),
                        SEEN_TTL_DAYS, TimeUnit.DAYS);
        if (!Boolean.TRUE.equals(firstTime)) {
            log.info("[push-mq] duplicate eventId={}, skip", event.getEventId());
            return;
        }

        // 2. 按 postId 回查最新数据（不直接信任消息载荷，避免快照过期）
        Postings post = postingsRepository.findById(event.getPostId()).orElse(null);
        if (post == null) {
            log.warn("[push-mq] postId={} not found in DB, skip", event.getPostId());
            return;
        }

        // 3. 加入候选池（daily / weekly / type）+ 维护热度榜
        candidatePoolService.addToPools(post);
        hotRankService.refreshPost(post);
        log.info("[push-mq] consumed eventId={}, postId={}, type={}",
                event.getEventId(), event.getPostId(), event.getType());
    }
}
