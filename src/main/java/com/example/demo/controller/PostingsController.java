package com.example.demo.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.Postings;
import com.example.demo.enums.LikeBizType;
import com.example.demo.service.IPostingsService;
import com.example.demo.service.LikeService;
import com.example.demo.util.PageParamUtil;
import io.swagger.annotations.*;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子相关接口
 * 提供帖子创建、查询、点赞等功能
 */
@Api(tags = "帖子管理", description = "提供帖子的创建、查询、点赞、删除等接口")
@RestController
@RequestMapping("/postings")
public class PostingsController {

    @Resource
    private IPostingsService postingsService;
    @Resource
    private LikeService likeService;

    @SaCheckLogin
    @PostMapping
    public SaResult create(@RequestBody Postings postings) {
        return SaResult.ok().setData(postingsService.createPost(postings));
    }

    @GetMapping("/{postingsId}")
    public SaResult getpost(@PathVariable Long postingsId) {
        return SaResult.ok().setData(postingsService.getPost(postingsId));
    }

    @SaCheckLogin
    @PutMapping("/{postingsId}")
    public SaResult update(@PathVariable Long postingsId, @RequestBody Postings postings) {
        postings.setPostingsId(postingsId);
        return SaResult.ok().setData(postingsService.updatePost(postings));
    }

    @SaCheckLogin
    @DeleteMapping("/{postingsId}")
    public SaResult delete(@PathVariable Long postingsId) {
        postingsService.removePost(postingsId);
        return SaResult.ok("删除成功");
    }

    @SaCheckLogin
    @PostMapping("/{postingsId}/like")
    public SaResult toggleLike(@PathVariable Long postingsId,
                               @RequestParam boolean like) {
        Long userId = StpUtil.getLoginIdAsLong();
        int count = postingsService.togglePostLike(postingsId, userId, like);
        return SaResult.ok().setData(count);
    }

    @GetMapping("/{postingsId}/like")
    public SaResult likeStatus(@PathVariable Long postingsId) {
        // 如果用户已登录，返回点赞状态；未登录则返回默认值
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        boolean liked = userId != null && likeService.hasUserLiked(LikeBizType.POST, postingsId, userId);
        int count = likeService.getLikeCount(LikeBizType.POST, postingsId);
        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("count", count);
        return SaResult.ok().setData(data);
    }

    @GetMapping
    public SaResult listByPlanet(@RequestParam Long planetId,
                                 @RequestParam(required = false) Integer page,
                                 @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(postingsService.listByPlanetId(planetId, p, s));
    }

    @GetMapping("/search")
    public SaResult search(@RequestParam String keyword,
                           @RequestParam(required = false) Long planetId,
                           @RequestParam(required = false) Integer page,
                           @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(postingsService.searchPosts(keyword, planetId, p, s));
    }

    @SaCheckLogin
    @GetMapping("/liked")
    public SaResult listLiked(@RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        Long userId = StpUtil.getLoginIdAsLong();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(postingsService.listLikedPosts(userId, p, s));
    }
}
