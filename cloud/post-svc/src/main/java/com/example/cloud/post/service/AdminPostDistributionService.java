package com.example.cloud.post.service;

import com.example.cloud.post.entity.Postings;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 帖子分发服务（post-svc 端简化版）。
 * <p>
 * 单体里 AdminPostDistributionService 同步调用 CandidatePoolService + HotRankService（push-svc 域内的服务）。
 * 微服务拆分后这些服务都在 push-svc，post-svc 无法直接调用，应通过事件触发 push-svc 同步分发。
 * 当前简化版只 log + TODO；生产可以新增 PostStatusChangedEvent 通过 MQ 通知 push-svc。
 */
@Slf4j
@Service
public class AdminPostDistributionService {

    @Resource
    private NotificationPublishHelper notificationPublishHelper;

    /** 帖子状态/审核变化时同步分发到候选池和热度榜（当前 stub）。 */
    public void syncDistribution(Postings post) {
        if (post == null || post.getPostingsId() == null) return;
        log.info("[admin-distribute] TODO: send event to push-svc to refresh/remove postId={}, status={}, audit={}",
                post.getPostingsId(), post.getStatus(), post.getAuditStatus());
    }

    /**
     * 审核结果通知（通知作者 + 星球成员）。
     */
    public void notifyAuditResult(Postings post, Integer oldAuditStatus, Integer auditStatus) {
        if (post == null || post.getUserId() == null || auditStatus == null) return;
        if (oldAuditStatus != null && oldAuditStatus.equals(auditStatus)) return;
        if (auditStatus == 1) {
            notificationPublishHelper.notifyAuditApproved(post.getUserId(), post.getPostingsId(), post.getTitle());
        } else if (auditStatus == 2) {
            notificationPublishHelper.notifyAuditRejected(post.getUserId(), post.getPostingsId(), post.getTitle());
        }
    }
}
