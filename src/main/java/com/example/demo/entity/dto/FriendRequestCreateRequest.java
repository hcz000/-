package com.example.demo.entity.dto;

import lombok.Data;

@Data
public class FriendRequestCreateRequest {
    private Long targetUserId;
    private String message;
}
