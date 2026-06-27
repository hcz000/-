package com.example.cloud.post.controller;

import com.example.cloud.post.service.PostService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子服务的内部接口（供 user-svc 等通过 Feign 调用，不对外暴露）。
 */
@Slf4j
@RestController
@RequestMapping("/internal/post")
public class InternalPostController {

    @Resource
    private PostService postService;

    /**
     * 软删除某个用户的所有帖子。
     * <p>
     * 由 user-svc 在「账号注销」流程中通过 Feign 调用，
     * 调用链路被 Seata @GlobalTransactional 包裹。
     *
     * @return 受影响的帖子数
     */
    @DeleteMapping("/by-user/{userId}")
    public Integer deleteByUser(@PathVariable Long userId) {
        log.info("[internal] DELETE /internal/post/by-user/{}", userId);
        return postService.softDeletePostsByUser(userId);
    }

    @GetMapping("/by-user/{userId}/count")
    public Long countByUser(@PathVariable Long userId) {
        return postService.countLivePostsOfUser(userId);
    }
}
