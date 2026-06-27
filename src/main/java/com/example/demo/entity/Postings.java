package com.example.demo.entity;

import com.example.demo.util.SnowflakeIdGenerator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
/**
 * 帖子表
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

    // ---- JPA 关联：只读、懒加载、对 JSON 不可见。仅供 @EntityGraph 在列表查询中 JOIN 使用。
    // 注意：被 @Cacheable(CACHE_POSTINGS) 缓存的实体，author/planet 是未初始化代理，
    // 缓存命中后访问会抛 LazyInitializationException（open-in-view=false）。
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User author;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planet_id", insertable = false, updatable = false)
    private Planet planet;
}
