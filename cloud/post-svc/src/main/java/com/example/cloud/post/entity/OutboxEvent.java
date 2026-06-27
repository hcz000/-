package com.example.cloud.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息表（Outbox Pattern）
 * <p>
 * 业务事务里同时写 postings 和 outbox_event，
 * 后台调度器周期性扫描 PENDING 行 → 发到 RabbitMQ → 标记 SENT。
 * 保证「业务和事件一定一起成功」，避免「业务提交了但 MQ 没收到」的不一致。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "outbox_event")
public class OutboxEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /** JSON 序列化的事件载荷 */
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public enum Status {
        /** 已写入 outbox，等待调度器发送 */
        PENDING,
        /** 已成功投递到 MQ */
        SENT,
        /** 超过重试次数仍失败，需人工介入 */
        FAILED
    }
}
