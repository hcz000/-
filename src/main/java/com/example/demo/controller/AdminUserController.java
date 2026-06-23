package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AdminAuthService;
import com.example.demo.service.IUserService;
import com.example.demo.util.PageParamUtil;
import jakarta.annotation.Resource;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminUserController {

    @Resource
    private AdminAuthService adminAuthService;
    @Resource
    private IUserService userService;
    @Resource
    private UserRepository userRepository;

    @GetMapping("/users")
    public SaResult getUsers(@RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size,
                             @RequestParam(required = false) String keyword) {
        adminAuthService.requireAdminId();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<User> pageData = userService.getUserList(keyword, p, s);
        return SaResult.ok().setData(pageData);
    }

    @GetMapping("/users/{userId}")
    public SaResult getUserDetail(@PathVariable Long userId) {
        adminAuthService.requireAdminId();
        return SaResult.ok().setData(requireUser(userId));
    }

    @PutMapping("/users/{userId}/status")
    public SaResult updateUserStatus(@PathVariable Long userId, @RequestParam String status) {
        adminAuthService.requireAdminId();
        User user = requireUser(userId);
        user.setRole(status);
        userService.updateById(user);
        return SaResult.ok("用户状态已更新");
    }

    @PostMapping("/kickout")
    public SaResult kickout(@RequestParam Long userId) {
        adminAuthService.requireAdminId();
        User user = requireUser(userId);
        StpUtil.kickout(userId);
        return SaResult.ok("已将用户 " + user.getUsername() + " 踢下线");
    }

    @PostMapping("/kickout-batch")
    public SaResult kickoutBatch(@RequestBody List<Long> userIds) {
        adminAuthService.requireAdminId();
        if (userIds == null || userIds.isEmpty()) {
            return SaResult.error("用户 ID 列表不能为空");
        }
        int count = 0;
        for (Long userId : userIds) {
            try {
                StpUtil.kickout(userId);
                count++;
            } catch (Exception e) {
                log.warn("kickout user failed, userId={}", userId, e);
            }
        }
        return SaResult.ok("成功踢出 " + count + " 个用户");
    }

    @PostMapping("/forceLogout")
    public SaResult forceLogout(@RequestParam Long userId) {
        adminAuthService.requireAdminId();
        User user = requireUser(userId);
        StpUtil.logout(userId);
        return SaResult.ok("已强制用户 " + user.getUsername() + " 注销");
    }

    @DeleteMapping("/users/{userId}")
    public SaResult deleteUser(@PathVariable Long userId) {
        adminAuthService.requireAdminId();
        requireUser(userId);
        userRepository.deleteById(userId);
        return SaResult.ok("用户已删除");
    }

    @DeleteMapping("/users/batch")
    public SaResult deleteUsersBatch(@RequestBody List<Long> userIds) {
        adminAuthService.requireAdminId();
        if (userIds == null || userIds.isEmpty()) {
            return SaResult.error("用户 ID 列表不能为空");
        }
        userRepository.deleteAllById(userIds);
        return SaResult.ok("批量删除成功");
    }

    private User requireUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
