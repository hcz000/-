package com.example.demo.entity.dto;

import com.example.demo.entity.TNotification;
import com.example.demo.enums.NotificationType;
import lombok.Data;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@RegisterReflectionForBinding(NotificationResponse.class)
public class NotificationResponse {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private Integer type;
    private String title;
    private String content;
    private Boolean readFlag;
    private String createTime;
    private Long postId;
    private String link;

    public static NotificationResponse fromEntity(TNotification notification) {
        NotificationResponse response = new NotificationResponse();
        if (notification == null) {
            return response;
        }
        response.setId(notification.getId());
        response.setType(notification.getType());
        NotificationType notificationType = NotificationType.fromCode(notification.getType());
        response.setTitle(notificationType != null ? notificationType.getTitle() : "通知");
        response.setContent(notification.getContent());
        response.setReadFlag(notification.getReadFlag());
        LocalDateTime createTime = notification.getCreateTime();
        response.setCreateTime(createTime != null ? createTime.format(FORMATTER) : null);
        response.setPostId(notification.getPostId());
        response.setLink(notification.getRelatedId());
        return response;
    }
}
