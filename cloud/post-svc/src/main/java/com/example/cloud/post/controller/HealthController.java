package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * post-svc 临时占位 Controller。
 * <p>
 * 当前仅暴露 health 探针，证明服务能起来。
 * 实际帖子 / 评论 / 星球 / 审核业务代码后续从单体搬迁。
 */
@RestController
@RequestMapping("/post")
public class HealthController {

    @GetMapping("/health")
    public SaResult health() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "post-svc");
        info.put("status", "skeleton");
        info.put("message", "post-svc 骨架就位，业务代码迁移中");
        return SaResult.ok().setData(info);
    }
}
