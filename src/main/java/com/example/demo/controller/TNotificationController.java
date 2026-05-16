package com.example.demo.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.TNotification;
import com.example.demo.entity.dto.NotificationResponse;
import com.example.demo.service.ITNotificationService;
import com.example.demo.util.PageParamUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Api(tags = "通知", description = "提供通知查询、标记已读、删除等功能")
@RestController
@RequestMapping("/t-notification")
public class TNotificationController {

    @Resource
    private ITNotificationService notificationService;

    @SaCheckLogin
    @GetMapping
    public SaResult list(@RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size,
                         @RequestParam(required = false) Boolean readFlag) {
        Long userId = StpUtil.getLoginIdAsLong();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 20, 100);
        Page<TNotification> pageData = notificationService.pageNotifications(userId, p, s, readFlag);
        return SaResult.ok().setData(pageData.map(NotificationResponse::fromEntity));
    }

    @SaCheckLogin
    @PostMapping("/{notificationId}/read")
    public SaResult markAsRead(@PathVariable Long notificationId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean success = notificationService.markAsRead(userId, notificationId);
        if (!success) {
            SaResult result = new SaResult();
            result.setCode(404);
            result.setMsg("通知不存在或无权操作");
            return result;
        }
        return SaResult.ok("操作成功");
    }

    @SaCheckLogin
    @DeleteMapping("/{notificationId}")
    public SaResult delete(@PathVariable Long notificationId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean success = notificationService.deleteNotification(userId, notificationId);
        if (!success) {
            SaResult result = new SaResult();
            result.setCode(404);
            result.setMsg("通知不存在或无权操作");
            return result;
        }
        return SaResult.ok("删除成功");
    }

    @SaCheckLogin
    @DeleteMapping("/clear")
    public SaResult clearAll() {
        Long userId = StpUtil.getLoginIdAsLong();
        notificationService.clearNotifications(userId);
        return SaResult.ok("已清空通知");
    }

    @SaCheckLogin
    @GetMapping("/unread-count")
    public SaResult unreadCount() {
        Long userId = StpUtil.getLoginIdAsLong();
        int count = notificationService.getUnreadCount(userId);
        return SaResult.ok().setData(count);
    }
}
