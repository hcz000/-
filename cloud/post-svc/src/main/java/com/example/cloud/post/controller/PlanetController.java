package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.post.entity.Planet;
import com.example.cloud.post.service.PlanetService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/planet")
public class PlanetController {

    @Resource
    private PlanetService planetService;

    @PostMapping
    public SaResult create(@RequestBody Planet planet,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        if (userId == null) return SaResult.error("用户未登录").setCode(401);
        if (planet.getMaster() == null) planet.setMaster(userId);
        return SaResult.ok().setData(planetService.createPlanet(planet));
    }

    @GetMapping("/{planetId}")
    public SaResult detail(@PathVariable Long planetId) {
        return SaResult.ok().setData(planetService.getPlanet(planetId));
    }

    @DeleteMapping("/{planetId}")
    public SaResult delete(@PathVariable Long planetId,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        if (xUserId == null) return SaResult.error("用户未登录").setCode(401);
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
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(planetService.listPlanetMembers(planetId, p, s));
    }

    @GetMapping("/my")
    public SaResult listMyPlanets(@RequestHeader(value = "X-User-Id", required = false) String xUserId,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        Long userId = parseUserId(xUserId);
        if (userId == null) return SaResult.error("用户未登录").setCode(401);
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(planetService.listUserVisitedPlanets(userId, p, s));
    }

    @PostMapping("/{planetId}/join")
    public SaResult join(@PathVariable Long planetId,
                         @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        if (userId == null) return SaResult.error("用户未登录").setCode(401);
        planetService.joinPlanet(planetId, userId);
        return SaResult.ok("加入成功");
    }

    @PostMapping("/{planetId}/leave")
    public SaResult leave(@PathVariable Long planetId,
                          @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        if (userId == null) return SaResult.error("用户未登录").setCode(401);
        planetService.leavePlanet(planetId, userId);
        return SaResult.ok("退出成功");
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
