package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.dto.AdminBatchResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.post.entity.Postings;
import com.example.cloud.post.repository.PostingsRepository;
import com.example.cloud.post.service.AdminAuthService;
import com.example.cloud.post.service.AdminPostModerationService;
import com.example.cloud.post.service.PostService;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminPostController {

    @Resource
    private AdminAuthService adminAuthService;

    @Resource
    private PostService postService;

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private AdminPostModerationService postModerationService;

    @GetMapping("/posts")
    public SaResult getPosts(@RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size,
                             @RequestParam(required = false) Long planetId,
                             @RequestParam(required = false) Long userId,
                             @RequestParam(required = false) String keyword) {
        adminAuthService.requireAdminId();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<Postings> pageData = postService.getPostList(planetId, userId, keyword, p, s);
        return SaResult.ok().setData(pageData);
    }

    @DeleteMapping("/posts/{postId}")
    public SaResult deletePost(@PathVariable Long postId) {
        adminAuthService.requireAdminId();
        postService.removePost(postId);
        return SaResult.ok("帖子已删除");
    }

    @PutMapping("/posts/{postId}/status")
    public SaResult updatePostStatus(@PathVariable Long postId, @RequestParam Integer status) {
        Long adminId = adminAuthService.requireAdminId();
        postModerationService.updatePostStatus(postId, status, adminId);
        return SaResult.ok(status == 1 ? "帖子已通过" : "帖子已下架");
    }

    @PutMapping("/posts/{postId}/audit")
    public SaResult auditPost(@PathVariable Long postId, @RequestParam Integer auditStatus) {
        Long adminId = adminAuthService.requireAdminId();
        postModerationService.auditPost(postId, auditStatus, adminId);
        return SaResult.ok(auditMessage(auditStatus));
    }

    @PutMapping("/posts/batch/audit")
    public SaResult auditPostsBatch(@RequestBody List<Long> postIds, @RequestParam Integer auditStatus) {
        Long adminId = adminAuthService.requireAdminId();
        AdminBatchResult result = postModerationService.auditPosts(postIds, auditStatus, adminId);
        return SaResult.ok("批量审核完成，成功 " + result.successCount() + " 个，失败 " + result.failureCount() + " 个")
                .setData(result);
    }

    @PutMapping("/posts/batch/status")
    public SaResult updatePostsStatusBatch(@RequestBody List<Long> postIds, @RequestParam Integer status) {
        Long adminId = adminAuthService.requireAdminId();
        AdminBatchResult result = postModerationService.updatePostStatuses(postIds, status, adminId);
        return SaResult.ok("批量状态更新完成，成功 " + result.successCount() + " 个，失败 " + result.failureCount() + " 个")
                .setData(result);
    }

    @DeleteMapping("/posts/batch")
    public SaResult deletePostsBatch(@RequestBody List<Long> postIds) {
        adminAuthService.requireAdminId();
        AdminBatchResult result = postModerationService.deletePosts(postIds);
        return SaResult.ok("批量删除完成，成功 " + result.successCount() + " 个，失败 " + result.failureCount() + " 个")
                .setData(result);
    }

    @GetMapping("/audit/posts")
    public SaResult auditQueue(@RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size,
                               @RequestParam(required = false) Integer auditStatus) {
        adminAuthService.requireAdminId();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Specification<Postings> spec = (root, query, cb) -> {
            Predicate predicate = cb.equal(root.get("deleted"), false);
            predicate = cb.and(predicate, cb.equal(root.get("auditStatus"), auditStatus == null ? 0 : auditStatus));
            return predicate;
        };
        Page<Postings> pageData = postingsRepository.findAll(spec,
                PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "createTime")));
        return SaResult.ok().setData(pageData);
    }

    private String auditMessage(Integer auditStatus) {
        if (auditStatus == null) return "状态更新成功";
        return switch (auditStatus) {
            case 0 -> "设为待审核";
            case 1 -> "审核通过";
            case 2 -> "审核拒绝";
            default -> "状态更新成功";
        };
    }
}
