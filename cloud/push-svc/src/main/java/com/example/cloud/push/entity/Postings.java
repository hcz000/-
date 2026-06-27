package com.example.cloud.push.entity;

import com.example.cloud.common.util.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 帖子表（push-svc 视角）
 * <p>
 * 与单体版的差异：
 * <ul>
 *   <li>移除 {@code @ManyToOne author} / {@code planet} 关联（push 服务不需要 JOIN）</li>
 *   <li>共享单体的 {@code postings} 表（DB schema 暂未拆分）</li>
 * </ul>
 * TODO：未来分库后，push-svc 通过 Feign 调用 post-svc 取帖子详情，本实体可改为 DTO。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "postings")
public class Postings implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "postings_id", columnDefinition = "BIGINT")
    private Long postingsId;

    @PrePersist
    public void generateId() {
        if (this.postingsId == null) {
            this.postingsId = SnowflakeIdGenerator.nextId();
        }
        if (this.deleted == null) {
            this.deleted = false;
        }
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
        if (this.updateTime == null) {
            this.updateTime = this.createTime;
        }
    }

    @PreUpdate
    public void touchUpdateTime() {
        this.updateTime = LocalDateTime.now();
    }

    @Column(name = "planet_id")
    private Long planetId;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "status")
    private Integer status;

    @Column(name = "reply_count")
    private Integer replyCount;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "first_comment_id")
    private Long firstCommentId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "type")
    private String type;

    @Column(name = "images", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> images = new ArrayList<>();

    @Column(name = "audit_status")
    private Integer auditStatus;

    @Column(name = "deleted")
    private Boolean deleted;

    @Column(name = "user_id")
    private Long userId;
}
