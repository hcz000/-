package com.example.cloud.post.service;

import com.example.cloud.common.api.post.PostCreatedEvent;
import com.example.cloud.post.entity.Postings;
import com.example.cloud.post.repository.PostingsRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 帖子服务。
 * <p>
 * - 软删除帖子 → 配合 Seata AT 演示分布式事务
 * - 创建帖子 → 配合 Outbox 模式演示最终一致性
 */
@Slf4j
@Service
public class PostService {

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private OutboxEventService outboxEventService;

    /**
     * 创建帖子 + 同事务写 outbox 事件。
     * <p>
     * 本地事务保证业务表 INSERT 和 outbox INSERT 一起成功。
     * 调度器异步把 outbox 投递到 MQ，最终送达 push-svc。
     */
    @Transactional(rollbackFor = Exception.class)
    public Postings createPostWithOutbox(Postings post) {
        if (post.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (post.getStatus() == null) post.setStatus(1);
        if (post.getAuditStatus() == null) post.setAuditStatus(1);
        if (post.getReplyCount() == null) post.setReplyCount(0);
        if (post.getLikeCount() == null) post.setLikeCount(0);

        Postings saved = postingsRepository.save(post);

        // 同事务写 outbox
        PostCreatedEvent event = new PostCreatedEvent(
                UUID.randomUUID().toString(),
                saved.getPostingsId(),
                saved.getUserId(),
                saved.getPlanetId(),
                saved.getType(),
                LocalDateTime.now()
        );
        outboxEventService.saveCreated(event);

        log.info("[post] created postId={}, userId={}, outbox eventId={}",
                saved.getPostingsId(), saved.getUserId(), event.getEventId());
        return saved;
    }

    /**
     * 软删除某用户名下的所有帖子（Seata AT 模式分支事务）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int softDeletePostsByUser(Long userId) {
        if (userId == null) return 0;
        int affected = postingsRepository.softDeleteByUserId(userId);
        log.info("[post] soft delete posts by userId={}, affected={}", userId, affected);
        return affected;
    }

    public long countLivePostsOfUser(Long userId) {
        return userId == null ? 0 : postingsRepository.countByUserIdAndDeletedFalse(userId);
    }
}

