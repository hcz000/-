package com.example.demo.service;

import com.example.demo.entity.Postings;
import com.example.demo.exception.BusinessException;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AdminPostModerationService {

    @Resource
    private IPostingsService postingsService;
    @Resource
    private AdminPostDistributionService distributionService;
    @Resource
    private AdminOperationLogService adminOperationLogService;

    @Transactional(rollbackFor = Exception.class)
    public Postings updatePostStatus(Long postId, Integer status, Long adminId) {
        Postings post = requirePost(postId);
        post.setStatus(status);
        post.setUpdateTime(LocalDateTime.now());
        postingsService.updateById(post);
        distributionService.syncDistribution(post);
        adminOperationLogService.record(adminId, "POST_STATUS", "POST", postId, "status=" + status);
        return post;
    }

    @Transactional(rollbackFor = Exception.class)
    public Postings auditPost(Long postId, Integer auditStatus, Long adminId) {
        Postings post = requirePost(postId);
        Integer oldAuditStatus = post.getAuditStatus();
        applyAuditStatus(post, auditStatus);
        post.setUpdateTime(LocalDateTime.now());
        postingsService.updateById(post);
        distributionService.syncDistribution(post);
        distributionService.notifyAuditResult(post, oldAuditStatus, auditStatus);
        adminOperationLogService.record(adminId, "POST_AUDIT", "POST", postId,
                "auditStatus=" + auditStatus + ",status=" + post.getStatus());
        return post;
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminBatchResult auditPosts(List<Long> postIds, Integer auditStatus, Long adminId) {
        validateIds(postIds);
        int success = 0;
        List<Long> failedIds = new ArrayList<>();
        for (Long postId : postIds) {
            try {
                auditPost(postId, auditStatus, adminId);
                success++;
            } catch (Exception e) {
                failedIds.add(postId);
                log.warn("batch audit post failed, postId={}, auditStatus={}", postId, auditStatus, e);
            }
        }
        return AdminBatchResult.of(success, failedIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminBatchResult updatePostStatuses(List<Long> postIds, Integer status, Long adminId) {
        validateIds(postIds);
        int success = 0;
        List<Long> failedIds = new ArrayList<>();
        for (Long postId : postIds) {
            try {
                updatePostStatus(postId, status, adminId);
                success++;
            } catch (Exception e) {
                failedIds.add(postId);
                log.warn("batch update post status failed, postId={}, status={}", postId, status, e);
            }
        }
        return AdminBatchResult.of(success, failedIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminBatchResult deletePosts(List<Long> postIds) {
        validateIds(postIds);
        int success = 0;
        List<Long> failedIds = new ArrayList<>();
        for (Long postId : postIds) {
            try {
                postingsService.removePost(postId);
                success++;
            } catch (Exception e) {
                failedIds.add(postId);
                log.warn("batch delete post failed, postId={}", postId, e);
            }
        }
        return AdminBatchResult.of(success, failedIds);
    }

    private Postings requirePost(Long postId) {
        Postings post = postingsService.getById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        return post;
    }

    private void applyAuditStatus(Postings post, Integer auditStatus) {
        post.setAuditStatus(auditStatus);
        if (auditStatus == null) {
            return;
        }
        if (auditStatus == 1) {
            post.setStatus(1);
        } else if (auditStatus == 0 || auditStatus == 2) {
            post.setStatus(0);
        }
    }

    private void validateIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("ID 列表不能为空");
        }
    }
}
