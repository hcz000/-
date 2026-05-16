package com.example.demo.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.demo.service.PushService;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "推送", description = "提供推送功能")
@RestController
@RequestMapping("/push")
public class PushController {
    @Resource
    private PushService pushService;

    @GetMapping("/a")
    public SaResult push() {
        // 首页推送是公开的
        return SaResult.ok().setData(pushService.pushForCurrentUser());
    }

    @GetMapping("/interest")
    public SaResult interestPush() {
        // 首页推送是公开的
        return SaResult.ok().setData(pushService.likepush());
    }

    @GetMapping("/random")
    public SaResult randomPush() {
        // 首页推送是公开的
        return SaResult.ok().setData(pushService.push());
    }
}
