package com.example.demo.entity.dto;

import lombok.Data;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@Data
@RegisterReflectionForBinding(FriendSummary.class)
public class FriendSummary {
    private Long userId;
    private String username;
    private String avatar;
    private String email;
}
