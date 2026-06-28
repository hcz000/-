package com.example.cloud.post.task;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 帖子回复数 buffer 聚合 → Redis 累加 → 由 {@link ReplyCountSyncTask} 定时刷盘。
 * <p>
 * 同 LikeBufferTrigger，简化版未启用此路径（PrimaryCommentService 直接写 DB）。
 */
@Slf4j
@Component
public class ReplyCountBufferTrigger {

    private static final int BUFFER_SIZE = 10_000;
    private static final int BATCH_SIZE = 500;
    private static final int LINGER_SECONDS = 2;

    private static final String REPLY_COUNT_KEY_PREFIX = "postings:reply_count:";
    private static final String REPLY_COUNT_DIRTY_ZSET = "postings:reply_count:dirty";

    private final AtomicReference<ArrayDeque<ReplyCountEvent>> writeBuffer =
            new AtomicReference<>(new ArrayDeque<>(BUFFER_SIZE));
    private final AtomicReference<ArrayDeque<ReplyCountEvent>> readBuffer =
            new AtomicReference<>(new ArrayDeque<>(BUFFER_SIZE));

    private volatile int writeCount = 0;

    @Resource
    private ScheduledExecutorService virtualThreadSchedulerExecutor;

    private final StringRedisTemplate stringRedisTemplate;
    private final Tracer tracer;

    public ReplyCountBufferTrigger(StringRedisTemplate stringRedisTemplate, Tracer tracer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tracer = tracer;
    }

    @PostConstruct
    public void start() {
        virtualThreadSchedulerExecutor.scheduleWithFixedDelay(
                this::trySwap, LINGER_SECONDS, LINGER_SECONDS, TimeUnit.SECONDS);
        log.info("ReplyCountBufferTrigger started, linger={}s, batchSize={}, bufferSize={}",
                LINGER_SECONDS, BATCH_SIZE, BUFFER_SIZE);
    }

    @PreDestroy
    public void stop() {
        if (writeCount > 0) swapAndConsume();
        log.info("ReplyCountBufferTrigger stopped");
    }

    public void enqueue(Long postingsId, int delta) {
        if (postingsId == null || delta == 0) return;
        ArrayDeque<ReplyCountEvent> buffer = writeBuffer.get();
        synchronized (buffer) {
            if (buffer.size() >= BUFFER_SIZE) {
                trySwap();
                buffer = writeBuffer.get();
            }
            buffer.addLast(new ReplyCountEvent(postingsId, delta));
            writeCount++;
        }
        if (writeCount >= BATCH_SIZE) trySwap();
    }

    private void trySwap() {
        if (writeCount == 0) return;
        swapAndConsume();
    }

    private void swapAndConsume() {
        ArrayDeque<ReplyCountEvent> oldWrite;
        ArrayDeque<ReplyCountEvent> oldRead;
        synchronized (writeBuffer) {
            oldWrite = writeBuffer.get();
            oldRead = readBuffer.get();
            writeBuffer.set(oldRead);
            readBuffer.set(oldWrite);
            writeCount = 0;
            oldRead.clear();
        }
        Span span = tracer.nextSpan().name("buffer:reply.swap.consume");
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            span.tag("buffer.type", "reply_count");
            span.tag("buffer.events", String.valueOf(oldWrite.size()));
            consumeBuffer(oldWrite);
        } catch (Throwable e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private void consumeBuffer(ArrayDeque<ReplyCountEvent> buffer) {
        if (buffer.isEmpty()) return;
        Map<Long, Integer> aggregates = new HashMap<>();
        for (ReplyCountEvent event : buffer) {
            aggregates.merge(event.postingsId(), event.delta(), Integer::sum);
        }
        long timestamp = System.currentTimeMillis();
        int successCount = 0;
        for (Map.Entry<Long, Integer> entry : aggregates.entrySet()) {
            Long postingsId = entry.getKey();
            Integer totalDelta = entry.getValue();
            if (totalDelta == 0) continue;
            try {
                stringRedisTemplate.opsForValue().increment(REPLY_COUNT_KEY_PREFIX + postingsId, totalDelta);
                stringRedisTemplate.opsForZSet().add(REPLY_COUNT_DIRTY_ZSET, postingsId.toString(), timestamp);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to update reply count, postingsId={}, delta={}", postingsId, totalDelta, e);
            }
        }
        log.info("Reply count aggregation done: sourceEvents={}, updatedKeys={}", buffer.size(), successCount);
        buffer.clear();
    }

    private record ReplyCountEvent(Long postingsId, int delta) {}
}
