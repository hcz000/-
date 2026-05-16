package com.example.demo.entity;

import com.example.demo.util.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报记录表
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
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.nextId();
        }
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDING";
        }
    }

    /**
     * 举报人ID
     */
    @Column(name = "reporter_id")
    private Long reporterId;

    /**
     * 被举报对象类型：POST-帖子, COMMENT-评论, USER-用户
     */
    @Column(name = "target_type")
    private String targetType;

    /**
     * 被举报对象ID
     */
    @Column(name = "target_id")
    private Long targetId;

    /**
     * 举报原因类型：SPAM-垃圾信息, ABUSE-辱骂, ILLEGAL-违规内容, OTHER-其他
     */
    @Column(name = "reason_type")
    private String reasonType;

    /**
     * 举报详细描述
     */
    @Column(name = "reason_detail")
    private String reasonDetail;

    /**
     * 处理状态：PENDING-待处理, PROCESSING-处理中, RESOLVED-已处理, REJECTED-已驳回
     */
    @Column(name = "status")
    private String status;

    /**
     * 处理人ID（管理员）
     */
    @Column(name = "handler_id")
    private Long handlerId;

    /**
     * 处理结果描述
     */
    @Column(name = "handle_result")
    private String handleResult;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 处理时间
     */
    @Column(name = "handle_time")
    private LocalDateTime handleTime;
}
