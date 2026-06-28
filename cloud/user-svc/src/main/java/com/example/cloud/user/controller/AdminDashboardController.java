package com.example.cloud.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.cloud.user.service.AdminAuthService;
import com.example.cloud.user.service.AdminDashboardService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    @Resource
    private AdminAuthService adminAuthService;

    @Resource
    private AdminDashboardService adminDashboardService;

    @GetMapping("/online-users")
    public SaResult getOnlineUsers() {
        adminAuthService.requireAdminId();
        List<String> tokenList = StpUtil.searchTokenValue("", 0, -1, false);
        return SaResult.ok().setData(tokenList);
    }

    /**
     * 用户域的统计概览。
     * 完整 dashboard 需要前端再调 {@code /api/post/admin/stats/overview} 合并。
     */
    @GetMapping("/stats/overview")
    public SaResult getStatsOverview() {
        adminAuthService.requireAdminId();
        return SaResult.ok().setData(adminDashboardService.overview());
    }
}
