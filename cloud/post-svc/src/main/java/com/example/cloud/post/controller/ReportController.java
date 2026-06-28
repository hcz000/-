package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.post.entity.Report;
import com.example.cloud.post.service.ReportService;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report")
public class ReportController {

    @Resource
    private ReportService reportService;

    @PostMapping("/submit")
    public SaResult submit(@RequestParam String targetType,
                           @RequestParam Long targetId,
                           @RequestParam String reasonType,
                           @RequestParam(required = false) String reasonDetail,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long reporterId = parseUserId(xUserId);
        if (reporterId == null) return SaResult.error("用户未登录").setCode(401);
        return SaResult.ok(reportService.submitReport(reporterId, targetType, targetId, reasonType, reasonDetail));
    }

    /**
     * 管理员列表 —— 后续接入 admin 权限校验，当前 demo 仅判 X-User-Id 存在。
     */
    @GetMapping("/list")
    public SaResult list(@RequestParam(required = false) String status,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<Report> pageData = reportService.getReportList(status, p, s);
        return SaResult.ok().setData(pageData);
    }

    @PostMapping("/handle")
    public SaResult handle(@RequestParam Long reportId,
                           @RequestParam String status,
                           @RequestParam(required = false) String handleResult,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long handlerId = parseUserId(xUserId);
        if (handlerId == null) return SaResult.error("管理员未登录").setCode(401);
        return SaResult.ok(reportService.handleReport(handlerId, reportId, status, handleResult));
    }

    @GetMapping("/my")
    public SaResult myReports(@RequestHeader(value = "X-User-Id", required = false) String xUserId,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        Long userId = parseUserId(xUserId);
        if (userId == null) return SaResult.error("用户未登录").setCode(401);
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<Report> pageData = reportService.getMyReports(userId, p, s);
        return SaResult.ok().setData(pageData);
    }

    private Long parseUserId(String xUserId) {
        if (xUserId == null || xUserId.isBlank()) return null;
        try {
            return Long.parseLong(xUserId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
