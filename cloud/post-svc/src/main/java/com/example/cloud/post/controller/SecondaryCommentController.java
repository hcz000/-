package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.post.entity.SecondaryComment;
import com.example.cloud.post.enums.LikeBizType;
import com.example.cloud.post.service.LikeService;
import com.example.cloud.post.service.SecondaryCommentService;
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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/secondary-comment")
public class SecondaryCommentController {

    @Resource
    private SecondaryCommentService secondaryCommentService;

    @Resource
    private LikeService likeService;

    @PostMapping
    public SaResult create(@RequestBody SecondaryComment comment,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        if (userId == null) return SaResult.error("用户未登录").setCode(401);
        comment.setUserId(userId);
        return SaResult.ok().setData(secondaryCommentService.createComment(comment));
    }

    @DeleteMapping("/{commentId}")
    public SaResult delete(@PathVariable Long commentId,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        if (xUserId == null) return SaResult.error("用户未登录").setCode(401);
        secondaryCommentService.removeComment(commentId);
        return SaResult.ok("删除成功");
    }

    @PostMapping("/{commentId}/like")
    public SaResult toggleLike(@PathVariable Long commentId, @RequestParam boolean like,
                               @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        if (userId == null) return SaResult.error("用户未登录").setCode(401);
        return SaResult.ok().setData(secondaryCommentService.toggleLike(commentId, userId, like));
    }

    @GetMapping("/{commentId}/like")
    public SaResult likeStatus(@PathVariable Long commentId,
                               @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        boolean liked = userId != null && likeService.hasUserLiked(LikeBizType.SECONDARY_COMMENT, commentId, userId);
        int count = likeService.getLikeCount(LikeBizType.SECONDARY_COMMENT, commentId);
        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("count", count);
        return SaResult.ok().setData(data);
    }

    @GetMapping
    public SaResult list(@RequestParam Long primaryCommentId,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(secondaryCommentService.listByPrimaryComment(primaryCommentId, p, s));
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
