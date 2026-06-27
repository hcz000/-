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
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.time.LocalDateTime;
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

    // ---- JPA 关联：只读、懒加载、对 JSON 不可见。仅供 @EntityGraph 使用。
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planet_id", insertable = false, updatable = false)
    private Planet planet;

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
