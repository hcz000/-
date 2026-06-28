package com.example.cloud.user.dto;

import lombok.Data;

@Data
public class ChatMessageSendRequest {
    private Long friendId;
    private String content;
}
