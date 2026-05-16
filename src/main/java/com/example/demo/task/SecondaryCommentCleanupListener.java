package com.example.demo.task;

import com.example.demo.entity.SecondaryComment;
import com.example.demo.repository.SecondaryCommentRepository;
import com.example.demo.service.CommentCacheService;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class SecondaryCommentCleanupListener {

    @Resource
    private SecondaryCommentRepository secondaryCommentRepository;
    @Resource
    private CommentCacheService commentCacheService;

    @RabbitListener(queues = CommentTopics.PRIMARY_COMMENT_DELETE)
    public void onMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            return;
        }
        Long primaryCommentId;
        try {
            primaryCommentId = Long.parseLong(payload.trim());
        } catch (NumberFormatException e) {
            log.warn("无效的一级评论 ID 载荷：{}", payload, e);
            return;
        }
        cleanPrimaryComment(primaryCommentId);
    }

    @Transactional
    private void cleanPrimaryComment(Long primaryCommentId) {
        try {
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
        } catch (Exception e) {
            log.warn("清理一级评论 {} 的二级评论失败", primaryCommentId, e);
        }
    }
}