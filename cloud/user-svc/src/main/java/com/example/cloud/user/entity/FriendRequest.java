package com.example.cloud.user.entity;

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

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "friend_request")
public class FriendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @PrePersist
    public void generateId() {
        if (this.id == null) this.id = SnowflakeIdGenerator.nextId();
        if (this.createTime == null) this.createTime = LocalDateTime.now();
        if (this.updateTime == null) this.updateTime = this.createTime;
    }

    @PreUpdate
    public void touchUpdateTime() {
        this.updateTime = LocalDateTime.now();
    }

    @Column(name = "requester_id")
    private Long requesterId;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "message")
    private String message;

    @Column(name = "status")
    private Integer status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
