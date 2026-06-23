package com.example.demo.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.PrimaryComment;
import com.example.demo.service.AdminAuthService;
import com.example.demo.service.IPrimaryCommentService;
import com.example.demo.util.PageParamUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminCommentController {

    @Resource
    private AdminAuthService adminAuthService;
    @Resource
    private IPrimaryCommentService primaryCommentService;

    @GetMapping("/comments")
    public SaResult getComments(@RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size,
                                @RequestParam(required = false) Long postId,
                                @RequestParam(required = false) Long userId) {
        adminAuthService.requireAdminId();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<PrimaryComment> pageData = primaryCommentService.getCommentList(postId, userId, p, s);
        return SaResult.ok().setData(pageData);
    }

    @DeleteMapping("/comments/{commentId}")
    public SaResult deleteComment(@PathVariable Long commentId) {
        adminAuthService.requireAdminId();
        primaryCommentService.removeComment(commentId);
        return SaResult.ok("评论已删除");
    }
}
