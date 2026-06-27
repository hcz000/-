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
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.time.LocalDateTime;
/**
 * 一级评论
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "primary_comment")

public class PrimaryComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.nextId();
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

    @Column(name = "postings_id")
    private Long postingsId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "content")
    private String content;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "deleted")
    private Boolean deleted;

    @Transient
    private Boolean firstComment;

    @Transient
    private Integer repliesTotal;

    // ---- JPA 关联：只读、懒加载、对 JSON 不可见。仅供 @EntityGraph 使用。
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
    @JoinColumn(name = "postings_id", insertable = false, updatable = false)
    private Postings post;
}
