package com.example.cloud.user.controller;

import com.example.cloud.common.api.user.UserInfoDTO;
import com.example.cloud.user.entity.User;
import com.example.cloud.user.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户内部接口（仅供其他微服务通过 Feign 调用）
 * <p>
 * 路径约定：{@code /internal/**}
 * <ul>
 *   <li>不需要登录鉴权（前提是网关层把 /internal/** 拦掉，不让外部访问）</li>
 *   <li>返回脱敏 DTO（{@link UserInfoDTO}），不含密码 / 邮箱</li>
 * </ul>
 *
 * 对外暴露的 {@link UserController#getUserInfo} 走 sa-token + SaResult，
 * 这里走 DTO + 直接返回，是两套不同语义的接口。
 */
@Slf4j
@RestController
@RequestMapping("/internal/user")
public class InternalUserController {

    @Resource
    private IUserService userService;

    @GetMapping("/{userId}")
    public UserInfoDTO getById(@PathVariable Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return null;
        }
        return toDto(user);
    }

    /**
     * 批量查用户脱敏信息。
     * <p>
     * 返回 Map<userId, UserInfoDTO>，调用方按 userId 取即可，
     * 找不到的 userId 不会在 Map 里出现。
     */
    @GetMapping("/batch")
    public Map<Long, UserInfoDTO> getByIds(@RequestParam("ids") List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, UserInfoDTO> result = new HashMap<>(userIds.size());
        for (Long id : userIds) {
            if (id == null) continue;
            User u = userService.getById(id);
            if (u != null) {
                result.put(u.getId(), toDto(u));
            }
        }
        log.debug("[internal] batch query {} ids, hit {}", userIds.size(), result.size());
        return result;
    }

    private UserInfoDTO toDto(User user) {
        return new UserInfoDTO(user.getId(), user.getUsername(), user.getAvatar());
    }

    /**
     * 返回用户角色字符串（admin / user 等）。
     * 供其他服务做 admin 权限校验时调用，避免反复传输 password 等敏感字段。
     */
    @GetMapping("/{userId}/role")
    public String getUserRole(@PathVariable Long userId) {
        User user = userService.getById(userId);
        return user == null ? "" : (user.getRole() == null ? "" : user.getRole());
    }
}
