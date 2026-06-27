package com.example.cloud.post.service;

import com.example.cloud.common.api.post.PostCreatedEvent;
import com.example.cloud.post.entity.OutboxEvent;
import com.example.cloud.post.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 业务事务里把事件写入 outbox 表（与业务表同一事务），
 * 由 {@link OutboxScheduler} 异步投递到 MQ。
 * <p>
 * 调用方必须在 {@code @Transactional} 内调用本类的方法。
 */
@Slf4j
@Service
public class OutboxEventService {

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void initMapper() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Resource
    private OutboxEventRepository outboxEventRepository;

    /**
     * 把 PostCreatedEvent 写入 outbox。
     * 调用方所在的本地事务保证「业务表 INSERT + outbox INSERT」原子。
     */
    public OutboxEvent saveCreated(PostCreatedEvent event) {
        OutboxEvent row = new OutboxEvent();
        row.setEventId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString());
        row.setAggregateId(event.getPostId());
        row.setEventType(PostCreatedEvent.EVENT_TYPE);
        row.setPayload(toJson(event));
        row.setStatus(OutboxEvent.Status.PENDING);
        row.setRetryCount(0);
        row.setCreatedAt(LocalDateTime.now());
        outboxEventRepository.save(row);
        log.debug("[outbox] saved eventId={}, postId={}", row.getEventId(), event.getPostId());
        return row;
    }

    private String toJson(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("serialize outbox payload failed", e);
        }
    }
}
