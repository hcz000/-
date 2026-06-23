package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.Planet;
import com.example.demo.service.IPlanetService;
import com.example.demo.util.PageParamUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/planet")
public class PlanetController {

    @Resource
    private IPlanetService planetService;

    @PostMapping
    public SaResult create(@RequestBody Planet planet) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录");
        }
        return SaResult.ok().setData(planetService.createPlanet(planet));
    }

    @GetMapping("/{planetId}")
    public SaResult detail(@PathVariable Long planetId) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录");
        }
        return SaResult.ok().setData(planetService.getPlanet(planetId));
    }

    @DeleteMapping("/{planetId}")
    public SaResult delete(@PathVariable Long planetId) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录");
        }
        planetService.removePlanet(planetId);
        return SaResult.ok("删除成功");
    }

    @GetMapping
    public SaResult list(@RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(planetService.listPlanets(p, s));
    }

    @GetMapping("/{planetId}/members")
    public SaResult listMembers(@PathVariable Long planetId,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录");
        }
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(planetService.listPlanetMembers(planetId, p, s));
    }

    @GetMapping("/my")
    public SaResult listMyPlanets(@RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(planetService.listUserVisitedPlanets(userId, p, s));
    }

    @PostMapping("/{planetId}/join")
    public SaResult join(@PathVariable Long planetId) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        planetService.joinPlanet(planetId, userId);
        return SaResult.ok("加入成功");
    }

    @PostMapping("/{planetId}/leave")
    public SaResult leave(@PathVariable Long planetId) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        planetService.leavePlanet(planetId, userId);
        return SaResult.ok("退出成功");
    }
}
