package com.example.demo.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.PrimaryComment;
import com.example.demo.enums.LikeBizType;
import com.example.demo.service.IPrimaryCommentService;
import com.example.demo.service.LikeService;
import com.example.demo.util.PageParamUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Api(tags = "一级评论接口", description = "提供帖子的一/级评论发布、删除、点赞、查询等功能")
@RestController
@RequestMapping("/primary-comment0")
public class PrimaryCommentController {

    @Resource
    private IPrimaryCommentService primaryCommentService;
    @Resource
    private LikeService likeService;

    @SaCheckLogin
    @PostMapping
    public SaResult create(@RequestBody PrimaryComment comment) {
        return SaResult.ok().setData(primaryCommentService.createComment(comment));
    }

    @SaCheckLogin
    @DeleteMapping("/{commentId}")
    public SaResult delete(@PathVariable Long commentId) {
        primaryCommentService.removeComment(commentId);
        return SaResult.ok("删除成功");
    }

    @SaCheckLogin
    @PostMapping("/{commentId}/like")
    public SaResult toggleLike(@PathVariable Long commentId, @RequestParam boolean like) {
        Long userId = StpUtil.getLoginIdAsLong();
        int count = primaryCommentService.toggleLike(commentId, userId, like);
        return SaResult.ok().setData(count);
    }

    @GetMapping("/{commentId}/like")
    public SaResult likeStatus(@PathVariable Long commentId) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        boolean liked = userId != null && likeService.hasUserLiked(LikeBizType.PRIMARY_COMMENT, commentId, userId);
        int count = likeService.getLikeCount(LikeBizType.PRIMARY_COMMENT, commentId);
        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("count", count);
        return SaResult.ok().setData(data);
    }

    @GetMapping
    public SaResult list(@RequestParam Long postingsId,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(primaryCommentService.listByPostings(postingsId, p, s));
    }
}
