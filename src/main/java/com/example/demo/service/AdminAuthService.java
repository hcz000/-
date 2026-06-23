package com.example.demo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    @Resource
    private IUserService userService;

    public Long requireAdminId() {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(currentUserId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"admin".equals(user.getRole())) {
            throw new BusinessException("无管理员权限");
        }
        return currentUserId;
    }

    public User requireAdminUser() {
        Long currentUserId = requireAdminId();
        User user = userService.getById(currentUserId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
