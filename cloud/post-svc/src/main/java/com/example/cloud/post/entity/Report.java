package com.example.cloud.post.entity;

import com.example.cloud.common.util.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报记录表。
 */
@Data
@Entity
@Table(name = "report")
public class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @PrePersist
    public void generateId() {
        if (this.id == null) this.id = SnowflakeIdGenerator.nextId();
        if (this.createTime == null) this.createTime = LocalDateTime.now();
        if (this.status == null || this.status.isBlank()) this.status = "PENDING";
    }

    @Column(name = "reporter_id")
    private Long reporterId;

    /** POST / COMMENT / USER */
    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    /** SPAM / ABUSE / ILLEGAL / OTHER */
    @Column(name = "reason_type")
    private String reasonType;

    @Column(name = "reason_detail")
    private String reasonDetail;

    /** PENDING / PROCESSING / RESOLVED / REJECTED */
    @Column(name = "status")
    private String status;

    @Column(name = "handler_id")
    private Long handlerId;

    @Column(name = "handle_result")
    private String handleResult;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "handle_time")
    private LocalDateTime handleTime;
}
