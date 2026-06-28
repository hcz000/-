package com.example.cloud.user.dto;

import lombok.Data;

@Data
public class FriendRequestCreateRequest {
    private Long targetUserId;
    private String message;
}
