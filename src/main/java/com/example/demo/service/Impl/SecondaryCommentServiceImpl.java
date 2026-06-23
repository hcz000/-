package com.example.demo.service.Impl;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.entity.Postings;
import com.example.demo.entity.PrimaryComment;
import com.example.demo.entity.SecondaryComment;
import com.example.demo.enums.LikeBizType;
import com.example.demo.enums.PostingType;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.PrimaryCommentRepository;
import com.example.demo.repository.SecondaryCommentRepository;
import com.example.demo.service.CommentCacheService;
import com.example.demo.service.CommentMentionService;
import com.example.demo.service.IPostingsService;
import com.example.demo.service.ISecondaryCommentService;
import com.example.demo.service.LikeService;
import com.example.demo.service.UserVectorBufferService;
import com.example.demo.task.NotificationSender;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class SecondaryCommentServiceImpl implements ISecondaryCommentService {

    @Resource
    private PrimaryCommentRepository primaryCommentRepository;
    @Resource
    private LikeService likeService;
    @Resource
    private CommentCacheService commentCacheService;
    @Resource
    private NotificationSender notificationSender;
    @Resource
    private CommentMentionService commentMentionService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IPostingsService postingsService;
    @Resource
    private UserVectorBufferService userVectorBufferService;
    @Resource
    private SecondaryCommentRepository secondaryCommentRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SecondaryComment createComment(SecondaryComment comment) {
        validateComment(comment);

        Long userId = currentUserId();
        String normalizedContent = normalizeContent(comment.getContent());

        String preventKey = "secondary_comment:prevent:" + userId + ":" + normalizedContent;

        Boolean setSuccess = stringRedisTemplate.opsForValue().setIfAbsent(preventKey, "1", java.time.Duration.ofSeconds(1));
        if (Boolean.FALSE.equals(setSuccess)) {
            throw new BusinessException("duplicate submit");
        }

        PrimaryComment parent = getValidPrimaryComment(comment.getPrimaryCommentId());
        if (comment.getPostingsId() != null && !parent.getPostingsId().equals(comment.getPostingsId())) {
            throw new BusinessException("post id does not match primary comment");
        }
        comment.setDeleted(false);
        comment.setLikeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount());
        comment.setPostingsId(parent.getPostingsId());
        comment.setUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        secondaryCommentRepository.save(comment);
        commentCacheService.putSecondaryComment(comment);
        notificationSender.notifyCommentReply(comment.getUserId(), parent.getUserId(), parent.getPostingsId(), comment.getId(), comment.getContent());
        if (comment.getParentId() != null) {
            SecondaryComment parentSecondary = commentCacheService.getSecondaryComment(comment.getParentId(),
                    () -> secondaryCommentRepository.findById(comment.getParentId()).orElse(null));
            if (parentSecondary != null) {
                notificationSender.notifyCommentReply(comment.getUserId(), parentSecondary.getUserId(), parent.getPostingsId(), comment.getId(), comment.getContent());
            }
        }
        commentMentionService.handleMentions(comment.getContent(), comment.getUserId(), comment.getId(), comment.getPostingsId());

        updateUserInterestVector(userId, comment.getPostingsId());

        return comment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeComment(Long commentId) {
        SecondaryComment existing = getExistingComment(commentId);
        existing.setDeleted(true);
        existing.setUpdateTime(LocalDateTime.now());
        secondaryCommentRepository.save(existing);
        commentCacheService.evictSecondary(commentId);
    }

    @Override
    public Page<SecondaryComment> listByPrimaryComment(Long primaryCommentId, int pageNum, int pageSize) {
        getValidPrimaryComment(primaryCommentId);
        Specification<SecondaryComment> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("primaryCommentId"), primaryCommentId));
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<SecondaryComment> result = secondaryCommentRepository.findAll(spec, pageable);
        Map<Long, Integer> likeCountMap = likeService.getLikeCountMap(
                LikeBizType.SECONDARY_COMMENT, collectSecondaryCommentIds(result));
        if (result != null && result.getContent() != null) {
            result.getContent().forEach(record -> {
                if (record != null) {
                    record.setLikeCount(likeCountMap.getOrDefault(record.getId(), 0));
                }
            });
        }
        return result;
    }

    @Override
    public int toggleLike(Long commentId, Long userId, boolean like) {
        getExistingComment(commentId);
        LikeService.LikeToggleResult result = likeService.toggleLikeWithChange(LikeBizType.SECONDARY_COMMENT, commentId, userId, like);
        return result.count();
    }

    private void validateComment(SecondaryComment comment) {
        if (comment == null) {
            throw new BusinessException("comment is required");
        }
        if (comment.getPrimaryCommentId() == null) {
            throw new BusinessException("primary comment id is required");
        }
        if (!StringUtils.hasText(comment.getContent())) {
            throw new BusinessException("comment content is required");
        }
    }

    private SecondaryComment getExistingComment(Long commentId) {
        if (commentId == null) {
            throw new BusinessException("secondary comment id is required");
        }
        SecondaryComment comment = loadSecondaryComment(commentId);
        if (comment == null) {
            throw new BusinessException("secondary comment not found");
        }
        return comment;
    }

    private PrimaryComment getValidPrimaryComment(Long primaryCommentId) {
        if (primaryCommentId == null) {
            throw new BusinessException("primary comment id is required");
        }
        PrimaryComment primaryComment = loadPrimaryComment(primaryCommentId);
        if (primaryComment == null) {
            throw new BusinessException("primary comment not found");
        }
        return primaryComment;
    }

    private Long currentUserId() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("user not logged in");
        }
        return StpUtil.getLoginIdAsLong();
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.trim();
    }

    private SecondaryComment loadSecondaryComment(Long commentId) {
        if (commentId == null) {
            return null;
        }
        SecondaryComment comment = commentCacheService.getSecondaryComment(commentId, () -> secondaryCommentRepository.findById(commentId).orElse(null));
        if (comment == null || Boolean.TRUE.equals(comment.getDeleted())) {
            return null;
        }
        return comment;
    }

    private PrimaryComment loadPrimaryComment(Long primaryCommentId) {
        if (primaryCommentId == null) {
            return null;
        }
        PrimaryComment primaryComment = commentCacheService.getPrimaryComment(primaryCommentId,
                () -> primaryCommentRepository.findById(primaryCommentId).orElse(null));
        if (primaryComment == null || (primaryComment.getDeleted() != null && primaryComment.getDeleted())) {
            return null;
        }
        return primaryComment;
    }

    private void updateUserInterestVector(Long userId, Long postingsId) {
        if (postingsId == null) {
            return;
        }
        try {
            Postings post = postingsService.getPost(postingsId);
            if (post != null && post.getType() != null) {
                PostingType postingType = PostingType.fromString(post.getType());
                if (postingType != null) {
                    userVectorBufferService.enqueue(userId, postingType);
                }
            }
        } catch (Exception e) {
            log.warn("更新用户兴趣向量失败: userId={}, postingsId={}, error={}", userId, postingsId, e.getMessage());
        }
    }

    private List<Long> collectSecondaryCommentIds(Page<SecondaryComment> page) {
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(page.getContent().size());
        for (SecondaryComment comment : page.getContent()) {
            if (comment != null && comment.getId() != null) {
                ids.add(comment.getId());
            }
        }
        return ids;
    }

    @Override
    public SecondaryComment save(SecondaryComment comment) {
        return secondaryCommentRepository.save(comment);
    }

    @Override
    public SecondaryComment getById(Long id) {
        return secondaryCommentRepository.findById(id).orElse(null);
    }

    @Override
    public boolean updateById(SecondaryComment comment) {
        if (comment == null || comment.getId() == null) {
            return false;
        }
        secondaryCommentRepository.save(comment);
        return true;
    }
}
