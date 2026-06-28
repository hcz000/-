package com.example.cloud.post.service;

import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.post.entity.SecondaryComment;
import com.example.cloud.post.enums.LikeBizType;
import com.example.cloud.post.repository.SecondaryCommentRepository;
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

@Slf4j
@Service
public class SecondaryCommentService {

    @Resource
    private SecondaryCommentRepository secondaryCommentRepository;

    @Resource
    private LikeService likeService;

    @Transactional(rollbackFor = Exception.class)
    public SecondaryComment createComment(SecondaryComment comment) {
        if (comment == null || comment.getPostingsId() == null
                || comment.getPrimaryCommentId() == null
                || !StringUtils.hasText(comment.getContent())) {
            throw new BusinessException("回复内容不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        comment.setLikeCount(0);
        comment.setDeleted(false);
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        return secondaryCommentRepository.save(comment);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeComment(Long commentId) {
        secondaryCommentRepository.findById(commentId).ifPresent(comment -> {
            comment.setDeleted(true);
            comment.setUpdateTime(LocalDateTime.now());
            secondaryCommentRepository.save(comment);
        });
    }

    public Page<SecondaryComment> listByPrimaryComment(Long primaryCommentId, int pageNum, int pageSize) {
        Specification<SecondaryComment> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("deleted"), false);
            p = cb.and(p, cb.equal(root.get("primaryCommentId"), primaryCommentId));
            return p;
        };
        return secondaryCommentRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.ASC, "createTime")));
    }

    public int toggleLike(Long commentId, Long userId, boolean like) {
        int count = likeService.toggleLike(LikeBizType.SECONDARY_COMMENT, commentId, userId, like);
        secondaryCommentRepository.findById(commentId).ifPresent(comment -> {
            comment.setLikeCount(count);
            secondaryCommentRepository.save(comment);
        });
        return count;
    }
}
