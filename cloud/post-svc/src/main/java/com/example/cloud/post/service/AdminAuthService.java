package com.example.cloud.post.service;

import com.example.cloud.common.api.user.UserFeignClient;
import com.example.cloud.common.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理员权限校验（post-svc 域内）。
 * <p>
 * 单体里 AdminAuthService 直接通过 sa-token 拿当前 userId 然后查 User 表查 role。
 * 在 microservices 拆分后：
 * <ul>
 *   <li>userId 通过网关注入的 {@code X-User-Id} 请求头获取（不再依赖 sa-token Redis 共享）</li>
 *   <li>role 通过 Feign 调 user-svc 的 {@code /internal/user/{userId}/role}</li>
 * </ul>
 */
@Slf4j
@Service
public class AdminAuthService {

    @Resource
    private UserFeignClient userFeignClient;

    public Long requireAdminId() {
        Long userId = currentUserId();
        if (userId == null) throw new BusinessException("请先登录");
        try {
            String role = userFeignClient.getUserRole(userId);
            if (!"admin".equals(role)) throw new BusinessException("无管理员权限");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[admin-auth] feign call user-svc failed, userId={}", userId, e);
            throw new BusinessException("权限校验失败：user-svc 不可用");
        }
        return userId;
    }

    private Long currentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest req = attrs.getRequest();
        String xUserId = req.getHeader("X-User-Id");
        if (xUserId == null || xUserId.isBlank()) return null;
        try {
            return Long.parseLong(xUserId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
