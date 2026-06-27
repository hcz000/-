package com.example.cloud.push.entity;

import com.example.cloud.common.util.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 星球成员（push-svc 视角，移除了 User / Planet 关联）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "planet_member")
public class PlanetMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.nextId();
        }
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
    }
}
