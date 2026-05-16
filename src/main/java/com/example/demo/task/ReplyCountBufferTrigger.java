package com.example.demo.task;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 评论数双 Buffer 聚合触发器
 *
 * 核心机制：
 * - Buffer A 和 Buffer B 交替使用
 * - 一个 Buffer 正在写入时，另一个 Buffer 正在消费
 * - 按 postingsId 分组聚合，合并多次评论为单次 Redis 写入
 * - 时间触发（linger）或数量触发（batchSize）时进行 Swap
 *
 * 优势：
 * - 短时间内同一帖子收到10条评论 → 合并为1次 Redis 调用
 * - 减少网络开销，提升性能
 */
@Slf4j
@Component
public class ReplyCountBufferTrigger {

    /**
     * 单个 Buffer 最大容量
     */
    private static final int BUFFER_SIZE = 10000;

    /**
     * 触发消费的数量阈值
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 时间触发间隔（秒）
     */
    private static final int LINGER_SECONDS = 2;

    /**
     * Redis key 前缀
     */
    private static final String REPLY_COUNT_KEY_PREFIX = "postings:reply_count:";
    private static final String REPLY_COUNT_DIRTY_ZSET = "postings:reply_count:dirty";

    /**
     * 写 Buffer（当前正在接收消息）
     */
    private final AtomicReference<ArrayDeque<ReplyCountEvent>> writeBuffer =
            new AtomicReference<>(new ArrayDeque<>(BUFFER_SIZE));

    /**
     * 读 Buffer（当前正在消费）
     */
    private final AtomicReference<ArrayDeque<ReplyCountEvent>> readBuffer =
            new AtomicReference<>(new ArrayDeque<>(BUFFER_SIZE));

    /**
     * 写 Buffer 当前大小
     */
    private volatile int writeCount = 0;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final StringRedisTemplate stringRedisTemplate;
    private final Tracer tracer;

    public ReplyCountBufferTrigger(StringRedisTemplate stringRedisTemplate, Tracer tracer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tracer = tracer;
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleWithFixedDelay(
                this::trySwap,
                LINGER_SECONDS,
                LINGER_SECONDS,
                TimeUnit.SECONDS);
        log.info("ReplyCountBufferTrigger 已启动，linger={}s, batchSize={}, bufferSize={}",
                LINGER_SECONDS, BATCH_SIZE, BUFFER_SIZE);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // 退出前处理剩余数据
        if (writeCount > 0) {
            swapAndConsume();
        }
        log.info("ReplyCountBufferTrigger 已停止");
    }

    /**
     * 评论数变更入队
     *
     * @param postingsId 帖子 ID
     * @param delta      变化量（+1 或 -1）
     */
    public void enqueue(Long postingsId, int delta) {
        if (postingsId == null || delta == 0) {
            return;
        }
        ArrayDeque<ReplyCountEvent> buffer = writeBuffer.get();
        synchronized (buffer) {
            if (buffer.size() >= BUFFER_SIZE) {
                trySwap();
                buffer = writeBuffer.get();
            }
            buffer.addLast(new ReplyCountEvent(postingsId, delta));
            writeCount++;
        }

        // 数量达到阈值，触发 Swap
        if (writeCount >= BATCH_SIZE) {
            trySwap();
        }
    }

    /**
     * 尝试 Swap（非阻塞）
     */
    private void trySwap() {
        if (writeCount == 0) {
            return;
        }
        swapAndConsume();
    }

    /**
     * Swap 并消费
     */
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

        log.debug("ReplyCount Swap 完成，准备消费 {} 条消息", oldWrite.size());
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

    /**
     * 消费 Buffer 中的消息
     * 按 postingsId 分组聚合，合并计算最终结果
     */
    private void consumeBuffer(ArrayDeque<ReplyCountEvent> buffer) {
        if (buffer.isEmpty()) {
            return;
        }

        // 1. 按 postingsId 分组聚合
        Map<Long, Integer> aggregates = new HashMap<>();
        for (ReplyCountEvent event : buffer) {
            aggregates.merge(event.postingsId(), event.delta(), Integer::sum);
        }

        // 2. 批量写入 Redis
        long timestamp = System.currentTimeMillis();
        int successCount = 0;

        for (Map.Entry<Long, Integer> entry : aggregates.entrySet()) {
            Long postingsId = entry.getKey();
            Integer totalDelta = entry.getValue();

            if (totalDelta == 0) {
                continue; // 最终无变更，跳过
            }

            try {
                String key = REPLY_COUNT_KEY_PREFIX + postingsId;
                // 原子更新计数
                stringRedisTemplate.opsForValue().increment(key, totalDelta);
                // 加入 dirty 集合，等待后续同步到 DB
                stringRedisTemplate.opsForZSet().add(REPLY_COUNT_DIRTY_ZSET, postingsId.toString(), timestamp);
                successCount++;
            } catch (Exception e) {
                log.warn("更新帖子 {} 评论数失败，delta={}", postingsId, totalDelta, e);
            }
        }

        log.info("评论数聚合完成：原 {} 条 → 聚合后 {} 条 Redis 写入",
                buffer.size(), successCount);

        // 3. 清空已消费的 Buffer
        buffer.clear();
    }

    /**
     * 评论数变更事件
     */
    private record ReplyCountEvent(Long postingsId, int delta) {
    }
}
