package com.example.demo.task;

import com.example.demo.enums.LikeBizType;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 双 Buffer 点赞聚合触发器
 *
 * 核心机制：
 * - Buffer A 和 Buffer B 交替使用
 * - 一个 Buffer 正在写入时，另一个 Buffer 正在消费
 * - 时间触发（linger）或数量触发（batchSize）时进行 Swap
 * - 按 bizId 分组聚合，合并多次操作为单次 DB 写入
 */
@Slf4j
@Component
public class LikeBufferTrigger {

    /**
     * 单个 Buffer 最大容量
     */
    private static final int BUFFER_SIZE = 50000;

    /**
     * 触发消费的数量阈值
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * 时间触发间隔（秒）
     */
    private static final int LINGER_SECONDS = 1;

    /**
     * 写 Buffer（当前正在接收消息）
     */
    private final AtomicReference<ArrayDeque<LikeChangeEvent>> writeBuffer =
            new AtomicReference<>(new ArrayDeque<>(BUFFER_SIZE));

    /**
     * 读 Buffer（当前正在消费）
     */
    private final AtomicReference<ArrayDeque<LikeChangeEvent>> readBuffer =
            new AtomicReference<>(new ArrayDeque<>(BUFFER_SIZE));

    /**
     * 写 Buffer 当前大小（避免频繁调用 size()）
     */
    private volatile int writeCount = 0;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final RabbitTemplate rabbitTemplate;
    private final Tracer tracer;

    public LikeBufferTrigger(RabbitTemplate rabbitTemplate, Tracer tracer) {
        this.rabbitTemplate = rabbitTemplate;
        this.tracer = tracer;
    }

    @PostConstruct
    public void start() {
        // 定时触发 Swap
        scheduler.scheduleWithFixedDelay(
                this::trySwap,
                LINGER_SECONDS,
                LINGER_SECONDS,
                TimeUnit.SECONDS);
        log.info("LikeBufferTrigger 已启动，linger={}s, batchSize={}, bufferSize={}",
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
        log.info("LikeBufferTrigger 已停止");
    }

    /**
     * 消息入队（写入当前写 Buffer）
     */
    public void enqueue(LikeChangeEvent event) {
        ArrayDeque<LikeChangeEvent> buffer = writeBuffer.get();
        synchronized (buffer) {
            if (buffer.size() >= BUFFER_SIZE) {
                // Buffer 满，触发 Swap
                trySwap();
                buffer = writeBuffer.get();
            }
            buffer.addLast(event);
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
     * 原子交换读写指针，然后消费读 Buffer
     */
    private void swapAndConsume() {
        ArrayDeque<LikeChangeEvent> oldWrite;
        ArrayDeque<LikeChangeEvent> oldRead;

        // 同步执行 Swap
        synchronized (writeBuffer) {
            oldWrite = writeBuffer.get();
            oldRead = readBuffer.get();

            // 交换指针
            writeBuffer.set(oldRead);
            readBuffer.set(oldWrite);

            // 重置计数
            writeCount = 0;

            // 清空新的写 Buffer（原来的读 Buffer）
            oldRead.clear();
        }

        log.debug("Swap 完成，准备消费 {} 条消息", oldWrite.size());

        // 消费读 Buffer（阻塞直到完成）
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

    /**
     * 消费 Buffer 中的消息
     * 按 bizId 分组聚合，合并计算最终结果
     */
    private void consumeBuffer(ArrayDeque<LikeChangeEvent> buffer) {
        if (buffer.isEmpty()) {
            return;
        }

        // 1. 按 bizId 分组聚合（合并同一对象的多次操作）
        Map<String, AggregateResult> countAggregates = new HashMap<>();
        Map<String, UserStateResult> userStateAggregates = new HashMap<>();

        for (LikeChangeEvent event : buffer) {
            String aggKey = event.aggregationKey();
            String userKey = event.userStateKey();

            // 聚合点赞计数变更
            countAggregates.compute(aggKey, (k, v) -> {
                if (v == null) {
                    return new AggregateResult(event.getBizType(), event.getBizId(), event.isLiked() ? 1 : -1);
                } else {
                    v.delta += event.isLiked() ? 1 : -1;
                    return v;
                }
            });

            // 聚合用户点赞状态（取最终状态）
            userStateAggregates.put(userKey, new UserStateResult(
                    event.getBizType(), event.getBizId(), event.getUserId(), event.isLiked()));
        }

        // 2. 处理聚合结果
        processAggregatedResults(countAggregates, userStateAggregates);

        // 3. 清空已消费的 Buffer
        buffer.clear();
    }

    /**
     * 处理聚合后的结果
     * 更新 Redis 并发送 MQ 落库
     */
    private void processAggregatedResults(
            Map<String, AggregateResult> countAggregates,
            Map<String, UserStateResult> userStateAggregates) {

        int countMessages = 0;
        int userMessages = 0;

        // 处理点赞计数（只处理有实际变更的）
        for (AggregateResult result : countAggregates.values()) {
            if (result.delta == 0) {
                continue;  // 最终无变更，跳过
            }

            // 发送聚合后的计数同步消息
            LikeSyncMessage message = new LikeSyncMessage(
                    result.bizType, result.bizId, result.delta);
            rabbitTemplate.convertAndSend(CommentTopics.LIKE_SYNC, message.serialize());
            countMessages++;

            log.debug("聚合计数消息：{}#{} delta={}", result.bizType, result.bizId, result.delta);
        }

        // 处理用户点赞状态
        for (UserStateResult result : userStateAggregates.values()) {
            LikeUserSyncMessage message = new LikeUserSyncMessage(
                    result.bizType, result.bizId, result.userId, result.liked);
            rabbitTemplate.convertAndSend(CommentTopics.LIKE_USER_SYNC, message.serialize());
            userMessages++;

            log.debug("用户状态消息：{}:{}:{} liked={}", result.bizType, result.bizId, result.userId, result.liked);
        }

        log.info("聚合完成：原 {} 条 → 计数消息 {} 条，用户状态消息 {} 条",
                countAggregates.size() + userStateAggregates.size(), countMessages, userMessages);
    }

    /**
     * 聚合结果（点赞计数变更）
     */
    private static class AggregateResult {
        final LikeBizType bizType;
        final Long bizId;
        int delta;  // +1 点赞, -1 取消点赞

        AggregateResult(LikeBizType bizType, Long bizId, int delta) {
            this.bizType = bizType;
            this.bizId = bizId;
            this.delta = delta;
        }
    }

    /**
     * 用户状态聚合结果
     */
    private static class UserStateResult {
        final LikeBizType bizType;
        final Long bizId;
        final Long userId;
        final boolean liked;

        UserStateResult(LikeBizType bizType, Long bizId, Long userId, boolean liked) {
            this.bizType = bizType;
            this.bizId = bizId;
            this.userId = userId;
            this.liked = liked;
        }
    }
}
