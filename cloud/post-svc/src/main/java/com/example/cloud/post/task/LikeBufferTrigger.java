package com.example.cloud.post.task;

import com.example.cloud.post.constant.CommentTopics;
import com.example.cloud.post.dto.LikeChangeEvent;
import com.example.cloud.post.dto.LikeSyncMessage;
import com.example.cloud.post.dto.LikeUserSyncMessage;
import com.example.cloud.post.enums.LikeBizType;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 点赞 buffer 聚合触发器。
 * <p>
 * 当前 LikeService 走简化路径（直接写 DB），此 buffer 路径未启用。
 * 保留迁移是为了演示「批处理优化」做法 —— 高并发场景下用双 buffer + 1s linger
 * 把 K 次单独事件聚合成 N 个 MQ 消息再批量更新 DB，性能数量级提升。
 */
@Slf4j
@Component
public class LikeBufferTrigger {

    private static final int BUFFER_SIZE = 50_000;
    private static final int BATCH_SIZE = 1_000;
    private static final int LINGER_SECONDS = 1;

    private final AtomicReference<ArrayDeque<LikeChangeEvent>> writeBuffer =
            new AtomicReference<>(new ArrayDeque<>(BUFFER_SIZE));
    private final AtomicReference<ArrayDeque<LikeChangeEvent>> readBuffer =
            new AtomicReference<>(new ArrayDeque<>(BUFFER_SIZE));

    private volatile int writeCount = 0;

    @Resource
    private ScheduledExecutorService virtualThreadSchedulerExecutor;

    private final RabbitTemplate rabbitTemplate;
    private final Tracer tracer;

    public LikeBufferTrigger(RabbitTemplate rabbitTemplate, Tracer tracer) {
        this.rabbitTemplate = rabbitTemplate;
        this.tracer = tracer;
    }

    @PostConstruct
    public void start() {
        virtualThreadSchedulerExecutor.scheduleWithFixedDelay(
                this::trySwap, LINGER_SECONDS, LINGER_SECONDS, TimeUnit.SECONDS);
        log.info("LikeBufferTrigger started, linger={}s, batchSize={}, bufferSize={}",
                LINGER_SECONDS, BATCH_SIZE, BUFFER_SIZE);
    }

    @PreDestroy
    public void stop() {
        if (writeCount > 0) swapAndConsume();
        log.info("LikeBufferTrigger stopped");
    }

    public void enqueue(LikeChangeEvent event) {
        ArrayDeque<LikeChangeEvent> buffer = writeBuffer.get();
        synchronized (buffer) {
            if (buffer.size() >= BUFFER_SIZE) {
                trySwap();
                buffer = writeBuffer.get();
            }
            buffer.addLast(event);
            writeCount++;
        }
        if (writeCount >= BATCH_SIZE) trySwap();
    }

    private void trySwap() {
        if (writeCount == 0) return;
        swapAndConsume();
    }

    private void swapAndConsume() {
        ArrayDeque<LikeChangeEvent> oldWrite;
        ArrayDeque<LikeChangeEvent> oldRead;
        synchronized (writeBuffer) {
            oldWrite = writeBuffer.get();
            oldRead = readBuffer.get();
            writeBuffer.set(oldRead);
            readBuffer.set(oldWrite);
            writeCount = 0;
            oldRead.clear();
        }
        Span span = tracer.nextSpan().name("buffer:like.swap.consume");
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            span.tag("buffer.type", "like");
            span.tag("buffer.events", String.valueOf(oldWrite.size()));
            consumeBuffer(oldWrite);
        } catch (Throwable e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private void consumeBuffer(ArrayDeque<LikeChangeEvent> buffer) {
        if (buffer.isEmpty()) return;
        Map<String, AggregateResult> countAggregates = new HashMap<>();
        Map<String, UserStateResult> userStateAggregates = new HashMap<>();
        for (LikeChangeEvent event : buffer) {
            String aggKey = event.aggregationKey();
            String userKey = event.userStateKey();
            countAggregates.compute(aggKey, (key, value) -> {
                if (value == null) {
                    return new AggregateResult(event.getBizType(), event.getBizId(), event.isLiked() ? 1 : -1);
                }
                value.delta += event.isLiked() ? 1 : -1;
                return value;
            });
            userStateAggregates.put(userKey, new UserStateResult(
                    event.getBizType(), event.getBizId(), event.getUserId(), event.isLiked()));
        }
        int countMessages = 0, userMessages = 0;
        for (AggregateResult r : countAggregates.values()) {
            if (r.delta == 0) continue;
            rabbitTemplate.convertAndSend(CommentTopics.LIKE_SYNC, new LikeSyncMessage(r.bizType, r.bizId, r.delta).serialize());
            countMessages++;
        }
        for (UserStateResult r : userStateAggregates.values()) {
            rabbitTemplate.convertAndSend(CommentTopics.LIKE_USER_SYNC,
                    new LikeUserSyncMessage(r.bizType, r.bizId, r.userId, r.liked).serialize());
            userMessages++;
        }
        log.info("Like aggregation done: aggregateMessages={}, userMessages={}", countMessages, userMessages);
        buffer.clear();
    }

    private static class AggregateResult {
        final LikeBizType bizType;
        final Long bizId;
        int delta;
        AggregateResult(LikeBizType bizType, Long bizId, int delta) {
            this.bizType = bizType; this.bizId = bizId; this.delta = delta;
        }
    }

    private static class UserStateResult {
        final LikeBizType bizType;
        final Long bizId;
        final Long userId;
        final boolean liked;
        UserStateResult(LikeBizType bizType, Long bizId, Long userId, boolean liked) {
            this.bizType = bizType; this.bizId = bizId; this.userId = userId; this.liked = liked;
        }
    }
}
