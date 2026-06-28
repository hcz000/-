package com.example.cloud.user.dto;

import lombok.Data;

@Data
public class FriendSummary {
    private Long userId;
    private String username;
    private String avatar;
    private String email;
}
