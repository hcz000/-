package com.example.cloud.post.listener;

import com.example.cloud.post.entity.SecondaryComment;
import com.example.cloud.post.event.PrimaryCommentDeletedEvent;
import com.example.cloud.post.repository.SecondaryCommentRepository;
import com.example.cloud.post.service.CommentCacheService;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一级评论删除 → 级联清理二级评论。
 * <p>
 * 用 Spring 事件 + @TransactionalEventListener(AFTER_COMMIT)：
 * 一级评论事务提交后才执行，主事务回滚则不触发；@Async 异步执行不阻塞调用方。
 */
@Slf4j
@Component
public class SecondaryCommentCleanupListener {

    @Resource
    private SecondaryCommentRepository secondaryCommentRepository;

    @Resource
    private CommentCacheService commentCacheService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onPrimaryCommentDeleted(PrimaryCommentDeletedEvent event) {
        Long primaryCommentId = event.primaryCommentId();
        if (primaryCommentId == null) return;
        try {
            cleanPrimaryComment(primaryCommentId);
        } catch (Exception e) {
            log.error("清理一级评论 {} 的二级评论失败", primaryCommentId, e);
        }
    }

    private void cleanPrimaryComment(Long primaryCommentId) {
        Specification<SecondaryComment> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("primaryCommentId"), primaryCommentId);
            p = cb.and(p, cb.equal(root.get("deleted"), false));
            return p;
        };
        List<SecondaryComment> secondaries = secondaryCommentRepository.findAll(spec);
        if (secondaries.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (SecondaryComment comment : secondaries) {
            comment.setDeleted(true);
            comment.setUpdateTime(now);
            secondaryCommentRepository.save(comment);
            commentCacheService.evictSecondary(comment.getId());
        }
    }
}
