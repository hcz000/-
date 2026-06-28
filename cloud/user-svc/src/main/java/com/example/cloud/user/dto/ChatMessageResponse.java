package com.example.cloud.user.dto;

import com.example.cloud.user.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageResponse {

    private Long id;
    private String conversationId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Boolean readFlag;
    private LocalDateTime createTime;

    public static ChatMessageResponse from(ChatMessage message) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setConversationId(message.getConversationId());
        response.setSenderId(message.getSenderId());
        response.setReceiverId(message.getReceiverId());
        response.setContent(message.getContent());
        response.setReadFlag(message.getReadFlag());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}
