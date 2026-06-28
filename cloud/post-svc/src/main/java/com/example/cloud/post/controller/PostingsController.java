package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.post.entity.Postings;
import com.example.cloud.post.enums.LikeBizType;
import com.example.cloud.post.service.LikeService;
import com.example.cloud.post.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 帖子对外业务接口。
 * <p>
 * 鉴权策略：所有写操作要求请求头携带 {@code X-User-Id}（由网关验证 token 后注入）。
 * 测试时可直接 curl 加 -H "X-User-Id: xxx" 模拟。
 */
@RestController
@RequestMapping("/post")
public class PostingsController {

    @Resource
    private PostService postService;

    @Resource
    private LikeService likeService;

    @PostMapping("/create")
    public SaResult create(@RequestBody Postings post,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = resolveUserId(xUserId, post.getUserId());
        if (userId == null) return SaResult.error("X-User-Id 不能为空").setCode(401);
        post.setUserId(userId);
        return SaResult.ok().setData(postService.createPostWithOutbox(post));
    }

    @GetMapping("/{postingsId}")
    public SaResult getPost(@PathVariable Long postingsId) {
        return SaResult.ok().setData(postService.getPost(postingsId));
    }

    @PutMapping("/{postingsId}")
    public SaResult update(@PathVariable Long postingsId, @RequestBody Postings post,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        if (xUserId == null) return SaResult.error("用户未登录").setCode(401);
        post.setPostingsId(postingsId);
        return SaResult.ok().setData(postService.updatePost(post));
    }

    @DeleteMapping("/{postingsId}")
    public SaResult delete(@PathVariable Long postingsId,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        if (xUserId == null) return SaResult.error("用户未登录").setCode(401);
        postService.removePost(postingsId);
        return SaResult.ok("删除成功");
    }

    @PostMapping("/{postingsId}/like")
    public SaResult toggleLike(@PathVariable Long postingsId,
                               @RequestParam boolean like,
                               @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        if (userId == null) return SaResult.error("用户未登录").setCode(401);
        return SaResult.ok().setData(postService.togglePostLike(postingsId, userId, like));
    }

    @GetMapping("/{postingsId}/like")
    public SaResult likeStatus(@PathVariable Long postingsId,
                               @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        boolean liked = userId != null && likeService.hasUserLiked(LikeBizType.POST, postingsId, userId);
        int count = likeService.getLikeCount(LikeBizType.POST, postingsId);
        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("count", count);
        return SaResult.ok().setData(data);
    }

    @GetMapping
    public SaResult listByPlanet(@RequestParam Long planetId,
                                 @RequestParam(required = false) LocalDateTime cursor,
                                 @RequestParam(required = false) Long lastId,
                                 @RequestParam(required = false) Integer size) {
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(postService.listByPlanetIdCursor(planetId, cursor, lastId, s));
    }

    @GetMapping("/search")
    public SaResult search(@RequestParam String keyword,
                           @RequestParam(required = false) Long planetId,
                           @RequestParam(required = false) Integer page,
                           @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(postService.searchPosts(keyword, planetId, p, s));
    }

    private Long parseUserId(String xUserId) {
        if (xUserId == null || xUserId.isBlank()) return null;
        try {
            return Long.parseLong(xUserId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolveUserId(String xUserId, Long fromBody) {
        Long id = parseUserId(xUserId);
        return id != null ? id : fromBody;
    }
}
