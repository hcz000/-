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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * 演示：展示当前请求里 sa-token 看到的 userId 和网关注入的 X-User-Id。
     * <p>
     * 在生产微服务架构里，鉴权下沉到网关后，下游服务可以选择：
     * <ol>
     *   <li>继续走 sa-token（依赖 Redis 共享会话）—— 当前默认方式</li>
     *   <li>直接读 {@code X-User-Id} header（无状态、零 Redis 依赖）—— 更轻量</li>
     * </ol>
     * 二者并存，方便后续逐步切换。
     */
    @GetMapping("/whoami")
    public SaResult whoami(@RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Map<String, Object> info = new HashMap<>(4);
        info.put("xUserId", xUserId);
        info.put("saLoginId", StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : null);
        info.put("authSource", xUserId != null ? "gateway-header" : "direct-access");
        info.put("note", "X-User-Id 由网关 HeaderEnrichGlobalFilter 注入；saLoginId 来自 sa-token + Redis 共享会话");
        return SaResult.ok().setData(info);
    }
}
