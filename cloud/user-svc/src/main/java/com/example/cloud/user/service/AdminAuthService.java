package com.example.cloud.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.user.entity.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 管理员权限校验服务（user-svc 域内）。
 */
@Service
public class AdminAuthService {

    @Resource
    private IUserService userService;

    public Long requireAdminId() {
        if (!StpUtil.isLogin()) throw new BusinessException("请先登录");
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (!"admin".equals(user.getRole())) throw new BusinessException("无管理员权限");
        return userId;
    }

    public User requireAdminUser() {
        Long userId = requireAdminId();
        User user = userService.getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        return user;
    }
}
