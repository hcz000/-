package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.post.entity.Postings;
import com.example.cloud.post.service.PostService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子对外业务接口（演示 Outbox 模式下的最终一致性）。
 * <p>
 * 鉴权说明：post-svc 没集成 sa-token Redis 共享，所以这里
 * 优先用 {@code X-User-Id}（由网关注入）；如果直连服务测试，可在请求体里带 userId。
 */
@Slf4j
@RestController
@RequestMapping("/post")
public class PostController {

    @Resource
    private PostService postService;

    /**
     * 创建帖子。
     * <p>
     * 本地事务：INSERT postings + INSERT outbox_event（PostCreatedEvent）一起 commit
     * 异步：OutboxScheduler 把事件投递到 MQ，push-svc 消费后加入候选池
     */
    @PostMapping("/create")
    public SaResult create(@RequestBody Postings post,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        if (post.getUserId() == null && xUserId != null) {
            try {
                post.setUserId(Long.parseLong(xUserId));
            } catch (NumberFormatException ignored) {
            }
        }
        if (post.getUserId() == null) {
            return SaResult.error("userId 不能为空（X-User-Id header 或请求体 userId 字段任选其一）").setCode(400);
        }
        Postings saved = postService.createPostWithOutbox(post);
        return SaResult.ok().setData(saved);
    }
}

