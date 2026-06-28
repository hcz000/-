package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.post.entity.Planet;
import com.example.cloud.post.entity.SystemConfig;
import com.example.cloud.post.service.AdminAuthService;
import com.example.cloud.post.service.AdminDashboardService;
import com.example.cloud.post.service.AdminOperationLogService;
import com.example.cloud.post.service.PlanetService;
import com.example.cloud.post.service.SystemConfigService;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminOpsController {

    @Resource
    private AdminAuthService adminAuthService;

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private AdminOperationLogService adminOperationLogService;

    @Resource
    private PlanetService planetService;

    @Resource
    private AdminDashboardService adminDashboardService;

    @GetMapping("/configs")
    public SaResult listConfigs() {
        adminAuthService.requireAdminId();
        return SaResult.ok().setData(systemConfigService.listAll());
    }

    @PutMapping("/configs")
    public SaResult upsertConfig(@RequestParam String key,
                                 @RequestParam String value,
                                 @RequestParam(required = false) String description) {
        Long adminId = adminAuthService.requireAdminId();
        SystemConfig config = systemConfigService.upsert(key, value, description);
        adminOperationLogService.record(adminId, "CONFIG_UPSERT", "SYSTEM_CONFIG", config.getId(), key + "=" + value);
        return SaResult.ok().setData(config);
    }

    @GetMapping("/planets")
    public SaResult getPlanets(@RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size,
                               @RequestParam(required = false) String name,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) Integer status) {
        adminAuthService.requireAdminId();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<Planet> pageData = planetService.getPlanetList(name, category, status, p, s);
        return SaResult.ok().setData(pageData);
    }

    @GetMapping("/stats/overview")
    public SaResult getStatsOverview() {
        adminAuthService.requireAdminId();
        return SaResult.ok().setData(adminDashboardService.overview());
    }

    // 单体里 AdminOpsController 还有 /hot/posts 和 /hot/rebuild 接口，
    // 它们直接调 push-svc 域的 HotRankService。微服务拆分后这两个端点
    // 应迁到 push-svc 自己的 admin 路由下，本 controller 不再实现。
}
