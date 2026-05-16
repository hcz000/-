package com.example.demo.entity.dto;

import lombok.Data;

@Data
public class ChatMessageSendRequest {
    private Long friendId;
    private String content;
}
