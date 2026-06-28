package com.example.cloud.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.user.dto.NotificationResponse;
import com.example.cloud.user.entity.TNotification;
import com.example.cloud.user.service.ITNotificationService;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/t-notification")
public class TNotificationController {

    @Resource
    private ITNotificationService notificationService;

    @GetMapping
    public SaResult list(@RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size,
                         @RequestParam(required = false) Boolean readFlag) {
        if (!StpUtil.isLogin()) return SaResult.error("未登录").setCode(401);
        Long userId = StpUtil.getLoginIdAsLong();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 20, 100);
        Page<TNotification> pageData = notificationService.pageNotifications(userId, p, s, readFlag);
        return SaResult.ok().setData(pageData.map(NotificationResponse::fromEntity));
    }

    @PostMapping("/{notificationId}/read")
    public SaResult markAsRead(@PathVariable Long notificationId) {
        if (!StpUtil.isLogin()) return SaResult.error("未登录").setCode(401);
        boolean success = notificationService.markAsRead(StpUtil.getLoginIdAsLong(), notificationId);
        return success ? SaResult.ok("操作成功") : SaResult.error("通知不存在或无权操作").setCode(404);
    }

    @DeleteMapping("/{notificationId}")
    public SaResult delete(@PathVariable Long notificationId) {
        if (!StpUtil.isLogin()) return SaResult.error("未登录").setCode(401);
        boolean success = notificationService.deleteNotification(StpUtil.getLoginIdAsLong(), notificationId);
        return success ? SaResult.ok("删除成功") : SaResult.error("通知不存在或无权操作").setCode(404);
    }

    @DeleteMapping("/clear")
    public SaResult clearAll() {
        if (!StpUtil.isLogin()) return SaResult.error("未登录").setCode(401);
        notificationService.clearNotifications(StpUtil.getLoginIdAsLong());
        return SaResult.ok("已清空通知");
    }

    @GetMapping("/unread-count")
    public SaResult unreadCount() {
        if (!StpUtil.isLogin()) return SaResult.error("未登录").setCode(401);
        return SaResult.ok().setData(notificationService.getUnreadCount(StpUtil.getLoginIdAsLong()));
    }
}
