package com.example.demo.entity.dto;

import com.example.demo.entity.ChatMessage;
import lombok.Data;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.time.LocalDateTime;

@Data
@RegisterReflectionForBinding(ChatMessageResponse.class)
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
