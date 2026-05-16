package com.example.demo.entity;

import com.example.demo.util.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_like")
public class UserLike implements Serializable {

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
        if (this.updateTime == null) {
            this.updateTime = this.createTime;
        }
        if (this.liked == null) {
            this.liked = true;
        }
    }

    @PreUpdate
    public void touchUpdateTime() {
        this.updateTime = LocalDateTime.now();
    }

    @Column(name = "biz_type")
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "liked")
    private Boolean liked;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
