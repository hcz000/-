package com.example.cloud.post.service;

import com.example.cloud.common.api.post.PostCreatedEvent;
import com.example.cloud.post.config.RabbitMQConfig;
import com.example.cloud.post.entity.OutboxEvent;
import com.example.cloud.post.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 投递调度器。
 * <p>
 * 每 1s 扫一次 PENDING 行 → 发到 RabbitMQ → 标记 SENT。
 * 单实例运行（多实例需要加分布式锁，本 demo 不做）。
 *
 * <h3>失败处理</h3>
 * 投递失败时 retry_count++、保留 PENDING，下一轮继续重试。
 * 超过 {@link #MAX_RETRY} 次标记为 FAILED，等待人工介入。
 */
@Slf4j
@Service
public class OutboxScheduler {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void initMapper() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Resource
    private OutboxEventRepository outboxEventRepository;

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * fixedDelay：上一次执行结束后等 1s 再开始下一次（避免并发跑同一批）。
     */
    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        List<OutboxEvent> batch;
        try {
            batch = outboxEventRepository.findPendingBatch(PageRequest.of(0, BATCH_SIZE));
        } catch (Exception e) {
            log.warn("[outbox-scheduler] fetch pending failed", e);
            return;
        }
        if (batch.isEmpty()) return;

        log.info("[outbox-scheduler] dispatch {} pending events", batch.size());
        for (OutboxEvent row : batch) {
            try {
                publishOne(row);
            } catch (Exception e) {
                handleFailure(row, e);
            }
        }
    }

    /**
     * 投递单个事件。每条事件用独立事务避免互相影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishOne(OutboxEvent row) throws JsonProcessingException {
        // 把 JSON payload 反序列化回 PostCreatedEvent，让 Jackson2JsonMessageConverter 在
        // 发送时再次序列化（这样消息 content_type=application/json，消费方好处理）。
        PostCreatedEvent event = mapper.readValue(row.getPayload(), PostCreatedEvent.class);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                PostCreatedEvent.ROUTING_KEY,
                event);

        row.setStatus(OutboxEvent.Status.SENT);
        row.setSentAt(LocalDateTime.now());
        outboxEventRepository.save(row);
        log.debug("[outbox-scheduler] published eventId={}, postId={}", row.getEventId(), event.getPostId());
    }

    private void handleFailure(OutboxEvent row, Exception e) {
        row.setRetryCount(row.getRetryCount() + 1);
        row.setLastError(truncate(e.toString(), 500));
        if (row.getRetryCount() >= MAX_RETRY) {
            row.setStatus(OutboxEvent.Status.FAILED);
            log.error("[outbox-scheduler] eventId={} marked FAILED after {} retries",
                    row.getEventId(), row.getRetryCount(), e);
        } else {
            log.warn("[outbox-scheduler] eventId={} retry {}/{}, reason={}",
                    row.getEventId(), row.getRetryCount(), MAX_RETRY, e.toString());
        }
        outboxEventRepository.save(row);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
