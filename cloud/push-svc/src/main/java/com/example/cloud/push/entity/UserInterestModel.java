package com.example.cloud.push.entity;

import com.example.cloud.common.util.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户兴趣模型表：每个 user × interest_key 一行。
 * <p>
 * interest_key 通常是 {@link com.example.cloud.push.enums.PostingType#name()}。
 * weight 用滑动平均（或简化为累计计数）维护；
 * event_count 用来判断「冷启动」阶段是否结束。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "user_interest_model",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_interest", columnNames = {"user_id", "interest_key"}))
public class UserInterestModel implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "interest_key", nullable = false, length = 64)
    private String interestKey;

    @Column(name = "weight", nullable = false)
    private Float weight;

    @Column(name = "event_count", nullable = false)
    private Integer eventCount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        if (id == null) id = SnowflakeIdGenerator.nextId();
        if (weight == null) weight = 0f;
        if (eventCount == null) eventCount = 0;
        if (createTime == null) createTime = LocalDateTime.now();
        if (updateTime == null) updateTime = createTime;
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
