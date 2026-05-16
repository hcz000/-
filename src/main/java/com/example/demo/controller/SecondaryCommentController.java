package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.SecondaryComment;
import com.example.demo.enums.LikeBizType;
import com.example.demo.service.ISecondaryCommentService;
import com.example.demo.service.LikeService;
import com.example.demo.util.PageParamUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Api(tags = "二级评论接口", description = "提供一级评论的二级回复发布、删除、点赞、查询等功能")
@RestController
@RequestMapping("/secondary-comment0")
public class SecondaryCommentController {

    @Resource
    private ISecondaryCommentService secondaryCommentService;
    @Resource
    private LikeService likeService;

    @PostMapping
    public SaResult create(@RequestBody SecondaryComment comment) {
        return SaResult.ok().setData(secondaryCommentService.createComment(comment));
    }

    @DeleteMapping("/{commentId}")
    public SaResult delete(@PathVariable Long commentId) {
        secondaryCommentService.removeComment(commentId);
        return SaResult.ok("删除成功");
    }

    @PostMapping("/{commentId}/like")
    public SaResult toggleLike(@PathVariable Long commentId, @RequestParam boolean like) {
        Long userId = StpUtil.getLoginIdAsLong();
        int count = secondaryCommentService.toggleLike(commentId, userId, like);
        return SaResult.ok().setData(count);
    }

    @GetMapping("/{commentId}/like")
    public SaResult likeStatus(@PathVariable Long commentId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean liked = likeService.hasUserLiked(LikeBizType.SECONDARY_COMMENT, commentId, userId);
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
}
