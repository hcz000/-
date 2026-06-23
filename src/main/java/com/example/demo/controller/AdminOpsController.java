package com.example.demo.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.Planet;
import com.example.demo.entity.SystemConfig;
import com.example.demo.service.AdminAuthService;
import com.example.demo.service.AdminOperationLogService;
import com.example.demo.service.HotRankService;
import com.example.demo.service.IPlanetService;
import com.example.demo.service.SystemConfigService;
import com.example.demo.util.PageParamUtil;
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
    private HotRankService hotRankService;
    @Resource
    private IPlanetService planetService;

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

    @GetMapping("/hot/posts")
    public SaResult hotPosts(@RequestParam(required = false) Integer size) {
        adminAuthService.requireAdminId();
        return SaResult.ok().setData(hotRankService.topPosts(size == null ? 20 : size));
    }

    @PostMapping("/hot/rebuild")
    public SaResult rebuildHot(@RequestParam(required = false) Integer limit) {
        Long adminId = adminAuthService.requireAdminId();
        int actualLimit = limit == null ? 500 : limit;
        hotRankService.rebuildRecent(actualLimit);
        adminOperationLogService.record(adminId, "HOT_REBUILD", "HOT_RANK", null, "limit=" + actualLimit);
        return SaResult.ok("热榜已重建");
    }

    @GetMapping("/info")
    public SaResult getAdminInfo() {
        return SaResult.ok().setData(adminAuthService.requireAdminUser());
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
}
