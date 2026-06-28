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

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "planet")
public class Planet implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "planet_id", columnDefinition = "BIGINT")
    private Long planetId;

    @PrePersist
    public void generateId() {
        if (this.planetId == null) this.planetId = SnowflakeIdGenerator.nextId();
        if (this.deleted == null) this.deleted = false;
        if (this.createTime == null) this.createTime = LocalDateTime.now();
        if (this.updateTime == null) this.updateTime = this.createTime;
    }

    @PreUpdate
    public void touchUpdateTime() {
        this.updateTime = LocalDateTime.now();
    }

    @Column(name = "name")
    private String name;

    @Column(name = "member_count")
    private Integer memberCount;

    @Column(name = "master")
    private Long master;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "deleted")
    private Boolean deleted;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "status")
    private Integer status;
}
