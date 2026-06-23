package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.service.PushService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/push")
public class PushController {

    @Resource
    private PushService pushService;

    @GetMapping("/a")
    public SaResult push() {
        return SaResult.ok().setData(pushService.pushForCurrentUser());
    }

    @GetMapping("/interest")
    public SaResult interestPush() {
        return SaResult.ok().setData(pushService.likepush());
    }

    @GetMapping("/random")
    public SaResult randomPush() {
        return SaResult.ok().setData(pushService.push());
    }

    @GetMapping("/hot")
    public SaResult hotPush(@RequestParam(required = false) Integer size) {
        return SaResult.ok().setData(pushService.hotPush(size == null ? 10 : size));
    }

    @GetMapping("/planet")
    public SaResult planetPush(@RequestParam(required = false) Integer size) {
        if (!StpUtil.isLogin()) {
            return SaResult.ok().setData(pushService.push());
        }
        return SaResult.ok().setData(pushService.planetPush(StpUtil.getLoginIdAsLong(), size == null ? 10 : size));
    }

    @GetMapping("/friends")
    public SaResult friendPush(@RequestParam(required = false) Integer size) {
        if (!StpUtil.isLogin()) {
            return SaResult.ok().setData(pushService.push());
        }
        return SaResult.ok().setData(pushService.friendPush(StpUtil.getLoginIdAsLong(), size == null ? 10 : size));
    }
}
