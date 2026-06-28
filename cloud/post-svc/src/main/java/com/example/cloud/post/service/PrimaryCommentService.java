package com.example.cloud.post.service;

import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.post.entity.PrimaryComment;
import com.example.cloud.post.enums.LikeBizType;
import com.example.cloud.post.repository.PostingsRepository;
import com.example.cloud.post.repository.PrimaryCommentRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 一级评论服务（简化版：去掉 cache / 通知 / FirstComment 标记 / N+1 优化）。
 */
@Slf4j
@Service
public class PrimaryCommentService {

    @Resource
    private PrimaryCommentRepository primaryCommentRepository;

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private LikeService likeService;

    @Transactional(rollbackFor = Exception.class)
    public PrimaryComment createComment(PrimaryComment comment) {
        if (comment == null || comment.getPostingsId() == null || !StringUtils.hasText(comment.getContent())) {
            throw new BusinessException("评论内容不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        comment.setLikeCount(0);
        comment.setDeleted(false);
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        PrimaryComment saved = primaryCommentRepository.save(comment);
        // 帖子回复数 +1
        postingsRepository.findById(saved.getPostingsId()).ifPresent(post -> {
            post.setReplyCount((post.getReplyCount() == null ? 0 : post.getReplyCount()) + 1);
            post.setUpdateTime(now);
            postingsRepository.save(post);
        });
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeComment(Long commentId) {
        primaryCommentRepository.findById(commentId).ifPresent(comment -> {
            comment.setDeleted(true);
            comment.setUpdateTime(LocalDateTime.now());
            primaryCommentRepository.save(comment);
        });
    }

    public Page<PrimaryComment> listByPostings(Long postingsId, int pageNum, int pageSize) {
        Specification<PrimaryComment> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("deleted"), false);
            p = cb.and(p, cb.equal(root.get("postingsId"), postingsId));
            return p;
        };
        return primaryCommentRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
    }

    public int toggleLike(Long commentId, Long userId, boolean like) {
        int count = likeService.toggleLike(LikeBizType.PRIMARY_COMMENT, commentId, userId, like);
        primaryCommentRepository.findById(commentId).ifPresent(comment -> {
            comment.setLikeCount(count);
            primaryCommentRepository.save(comment);
        });
        return count;
    }

    /**
     * 多条件查询（admin 用）。
     */
    public Page<PrimaryComment> getCommentList(Long postId, Long userId, int pageNum, int pageSize) {
        Specification<PrimaryComment> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("deleted"), false);
            if (postId != null) p = cb.and(p, cb.equal(root.get("postingsId"), postId));
            if (userId != null) p = cb.and(p, cb.equal(root.get("userId"), userId));
            return p;
        };
        return primaryCommentRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
    }
}
