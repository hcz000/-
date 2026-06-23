package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.IUserService;
import com.example.demo.service.IOssService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
/**
 * 用户控制器
 * 提供用户登录、注册、登出等功能
 */

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private IUserService userService;

    @Resource
    private IOssService ossService;

    @RequestMapping("/Login")
    public SaResult doLogin(@RequestParam String email, @RequestParam String password) {
        String token = userService.doLogin(email, password);
        return SaResult.ok().setData(token);
    }

    @RequestMapping("/registered")
    public SaResult registered(@RequestParam String username, @RequestParam String email, @RequestParam String password, @RequestParam String code) {
        return SaResult.ok(userService.registered(username, email, password, code));
    }

    @RequestMapping("/logout")
    public SaResult logout() {
        // 未登录时也允许调用 logout，不报错
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
    public SaResult updataUser(@RequestBody User user) {
        // 更新用户信息需要登录
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录").setCode(401);
        }
        return SaResult.ok(userService.updataUser(user));
    }

    @GetMapping("/profile")
    public SaResult profile() {
        // 获取用户信息 - 未登录时返回 null，不报错
        // 前端会根据返回值判断用户是否登录
        if (!StpUtil.isLogin()) {
            return SaResult.ok().setData(null);
        }
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            return SaResult.ok().setData(userService.getProfile(userId));
        } catch (BusinessException e) {
            // 用户已被删除，清除无效 token
            StpUtil.logout();
            return SaResult.ok().setData(null);
        }
    }

    @PostMapping("/avatar")
    public SaResult uploadAvatar(@RequestPart("file") MultipartFile file) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录").setCode(401);
        }
        String url = ossService.uploadImage(file);
        // 更新用户头像
        Long userId = StpUtil.getLoginIdAsLong();
        User user = new User();
        user.setId(userId);
        user.setAvatar(url);
        userService.updataUser(user);
        return SaResult.ok().setData(url);
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