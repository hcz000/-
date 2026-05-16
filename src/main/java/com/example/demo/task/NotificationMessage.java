package com.example.demo.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonDeserialize(as = NotificationMessage.class)
@JsonSerialize(as = NotificationMessage.class)
public class NotificationMessage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Long senderId;
    private Long recipientId;
    private Integer type;
    private Long postId;
    private String relatedId;
    private String content;

    public NotificationMessage() {
    }

    public NotificationMessage(Long senderId, Long recipientId, Integer type, Long postId, String relatedId, String content) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.type = type;
        this.postId = postId;
        this.relatedId = relatedId;
        this.content = content;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(String relatedId) {
        this.relatedId = relatedId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String serialize() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 NotificationMessage 失败", e);
        }
    }

    public static NotificationMessage deserialize(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, NotificationMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无效的通知载荷", e);
        }
    }
}
