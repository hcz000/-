package com.example.demo.task;

import com.example.demo.enums.LikeBizType;
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
                this::trySwap,
                LINGER_SECONDS,
                LINGER_SECONDS,
                TimeUnit.SECONDS);
        log.info("LikeBufferTrigger started, linger={}s, batchSize={}, bufferSize={}",
                LINGER_SECONDS, BATCH_SIZE, BUFFER_SIZE);
    }

    @PreDestroy
    public void stop() {
        if (writeCount > 0) {
            swapAndConsume();
        }
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
        if (writeCount >= BATCH_SIZE) {
            trySwap();
        }
    }

    private void trySwap() {
        if (writeCount == 0) {
            return;
        }
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

        log.debug("Like buffer swapped, events={}", oldWrite.size());

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
        if (buffer.isEmpty()) {
            return;
        }

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

        processAggregatedResults(countAggregates, userStateAggregates);
        buffer.clear();
    }

    private void processAggregatedResults(
            Map<String, AggregateResult> countAggregates,
            Map<String, UserStateResult> userStateAggregates) {

        int countMessages = 0;
        int userMessages = 0;

        for (AggregateResult result : countAggregates.values()) {
            if (result.delta == 0) {
                continue;
            }
            LikeSyncMessage message = new LikeSyncMessage(result.bizType, result.bizId, result.delta);
            rabbitTemplate.convertAndSend(CommentTopics.LIKE_SYNC, message.serialize());
            countMessages++;
            log.debug("Like aggregate dispatched, bizType={}, bizId={}, delta={}",
                    result.bizType, result.bizId, result.delta);
        }

        for (UserStateResult result : userStateAggregates.values()) {
            LikeUserSyncMessage message = new LikeUserSyncMessage(
                    result.bizType, result.bizId, result.userId, result.liked);
            rabbitTemplate.convertAndSend(CommentTopics.LIKE_USER_SYNC, message.serialize());
            userMessages++;
            log.debug("Like user state dispatched, bizType={}, bizId={}, userId={}, liked={}",
                    result.bizType, result.bizId, result.userId, result.liked);
        }

        log.info("Like aggregation done: aggregateMessages={}, userMessages={}", countMessages, userMessages);
    }

    private static class AggregateResult {
        final LikeBizType bizType;
        final Long bizId;
        int delta;

        private AggregateResult(LikeBizType bizType, Long bizId, int delta) {
            this.bizType = bizType;
            this.bizId = bizId;
            this.delta = delta;
        }
    }

    private static class UserStateResult {
        final LikeBizType bizType;
        final Long bizId;
        final Long userId;
        final boolean liked;

        private UserStateResult(LikeBizType bizType, Long bizId, Long userId, boolean liked) {
            this.bizType = bizType;
            this.bizId = bizId;
            this.userId = userId;
            this.liked = liked;
        }
    }
}
