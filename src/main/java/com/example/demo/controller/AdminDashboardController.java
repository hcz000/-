package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.service.AdminAuthService;
import com.example.demo.service.AdminDashboardService;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
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

    @GetMapping("/stats/overview")
    public SaResult getStatsOverview() {
        adminAuthService.requireAdminId();
        return SaResult.ok().setData(adminDashboardService.overview());
    }
}
