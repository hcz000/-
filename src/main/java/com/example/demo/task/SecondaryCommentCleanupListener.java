package com.example.demo.task;

import com.example.demo.entity.SecondaryComment;
import com.example.demo.repository.SecondaryCommentRepository;
import com.example.demo.service.CommentCacheService;
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
 * 一级评论删除 → 级联清理二级评论
 * <p>
 * 单体场景下从 RabbitMQ 切换到 Spring 事件：
 * - AFTER_COMMIT：一级评论事务提交后才执行，主事务回滚则不触发，避免数据错乱
 * - @Async：异步执行，不阻塞主调用方
 * <p>
 * 失败兜底：若清理失败，二级评论会变成「找不到一级评论」的孤儿数据，可由定时任务扫描清理。
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
        if (primaryCommentId == null) {
            return;
        }
        try {
            cleanPrimaryComment(primaryCommentId);
        } catch (Exception e) {
            // Spring 事件没有重试机制，记日志由定时任务兜底
            log.error("清理一级评论 {} 的二级评论失败", primaryCommentId, e);
        }
    }

    private void cleanPrimaryComment(Long primaryCommentId) {
        Specification<SecondaryComment> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("primaryCommentId"), primaryCommentId));
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            return predicate;
        };
        List<SecondaryComment> secondaries = secondaryCommentRepository.findAll(spec);
        if (secondaries.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SecondaryComment comment : secondaries) {
            comment.setDeleted(true);
            comment.setUpdateTime(now);
            secondaryCommentRepository.save(comment);
            commentCacheService.evictSecondary(comment.getId());
        }
    }
}
