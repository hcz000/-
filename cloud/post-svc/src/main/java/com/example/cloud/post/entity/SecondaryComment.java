package com.example.cloud.post.entity;

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

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 二级评论
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "secondary_comment")
public class SecondaryComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @PrePersist
    public void generateId() {
        if (this.id == null) this.id = SnowflakeIdGenerator.nextId();
        if (this.deleted == null) this.deleted = false;
        if (this.createTime == null) this.createTime = LocalDateTime.now();
        if (this.updateTime == null) this.updateTime = this.createTime;
    }

    @PreUpdate
    public void touchUpdateTime() {
        this.updateTime = LocalDateTime.now();
    }

    @Column(name = "postings_id")
    private Long postingsId;

    @Column(name = "primary_comment_id")
    private Long primaryCommentId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "reply_to_username")
    private String replyToUsername;

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
}
