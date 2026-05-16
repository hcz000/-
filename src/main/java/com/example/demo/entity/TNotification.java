package com.example.demo.entity;

import com.example.demo.util.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "t_notification")
@ApiModel(value="TNotification对象", description="通知表")
public class TNotification implements Serializable {

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
        if (this.readFlag == null) {
            this.readFlag = false;
        }
    }

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "recipient_id")
    private Long recipientId;

    @Column(name = "related_id")
    private String relatedId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "type")
    private Integer type;

    @Column(name = "content")
    private String content;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "read_flag")
    private Boolean readFlag;
}
