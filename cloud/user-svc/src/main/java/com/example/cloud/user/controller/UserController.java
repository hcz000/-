package com.example.cloud.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.user.entity.User;
import com.example.cloud.user.service.IUserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 * <p>
 * 与单体版相比：
 * <ul>
 *   <li>移除 {@code POST /avatar} 头像上传接口（依赖 OSS，后续作为独立 Feign 调用或直接走网关上传）</li>
 * </ul>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @RequestMapping("/Login")
    public SaResult doLogin(@RequestParam String email, @RequestParam String password) {
        String token = userService.doLogin(email, password);
        return SaResult.ok().setData(token);
    }

    @RequestMapping("/registered")
    public SaResult registered(@RequestParam String username,
                               @RequestParam String email,
                               @RequestParam String password,
                               @RequestParam String code) {
        return SaResult.ok(userService.registered(username, email, password, code));
    }

    @RequestMapping("/logout")
    public SaResult logout() {
        if (!StpUtil.isLogin()) {
            return SaResult.ok();
        }
        return SaResult.ok(userService.logout());
    }

    @RequestMapping("/send")
    public SaResult send(@RequestParam String email) {
        return SaResult.ok(userService.send(email));
    }

    @PostMapping("/updatauser")
    public SaResult updateUser(@RequestBody User user) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录").setCode(401);
        }
        return SaResult.ok(userService.updateUser(user));
    }

    @GetMapping("/profile")
    public SaResult profile() {
        if (!StpUtil.isLogin()) {
            return SaResult.ok().setData(null);
        }
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            return SaResult.ok().setData(userService.getProfile(userId));
        } catch (BusinessException e) {
            StpUtil.logout();
            return SaResult.ok().setData(null);
        }
    }

    @GetMapping("/search")
    public SaResult searchByEmail(@RequestParam String email) {
        User user = userService.findByEmail(email);
        if (user == null) {
            return SaResult.error("用户不存在").setCode(404);
        }
        return SaResult.ok().setData(user);
    }

    @GetMapping("/info/{userId}")
    public SaResult getUserInfo(@PathVariable Long userId) {
        User user = userService.getUserInfo(userId);
        if (user == null) {
            return SaResult.error("用户不存在").setCode(404);
        }
        return SaResult.ok().setData(user);
    }
}
