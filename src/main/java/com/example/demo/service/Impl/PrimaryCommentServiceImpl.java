package com.example.demo.service.Impl;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.entity.Postings;
import com.example.demo.entity.PrimaryComment;
import com.example.demo.enums.LikeBizType;
import com.example.demo.enums.PostingType;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.PrimaryCommentRepository;
import com.example.demo.repository.SecondaryCommentRepository;
import com.example.demo.service.CommentCacheService;
import com.example.demo.service.CommentMentionService;
import com.example.demo.service.FirstCommentService;
import com.example.demo.service.IPostingsService;
import com.example.demo.service.IPrimaryCommentService;
import com.example.demo.service.ISensitiveWordService;
import com.example.demo.service.LikeService;
import com.example.demo.service.UserVectorBufferService;
import com.example.demo.task.NotificationSender;
import com.example.demo.task.PrimaryCommentDeletedEvent;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


@Slf4j
@Service
public class PrimaryCommentServiceImpl implements IPrimaryCommentService {

    @Resource
    private IPostingsService postingsService;
    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private LikeService likeService;
    @Resource
    private CommentCacheService commentCacheService;
    @Resource
    private NotificationSender notificationSender;
    @Resource
    private FirstCommentService firstCommentService;
    @Resource
    private CommentMentionService commentMentionService;
    @Resource
    private SecondaryCommentRepository secondaryCommentRepository;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISensitiveWordService sensitiveWordService;
    @Resource
    private UserVectorBufferService userVectorBufferService;
    @Resource
    private PrimaryCommentRepository primaryCommentRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrimaryComment createComment(PrimaryComment comment) {
        validateComment(comment);

        Long userId = currentUserId();

        String normalizedContent = normalizeContent(comment.getContent());
        String preventKey = "primary_comment:prevent:" + userId + ":" + normalizedContent;

        Boolean setSuccess = stringRedisTemplate.opsForValue().setIfAbsent(preventKey, "1", java.time.Duration.ofSeconds(1));
        if (Boolean.FALSE.equals(setSuccess)) {
            throw new BusinessException("duplicate comment, please wait before resubmitting");
        }

        Postings post = postingsService.getPost(comment.getPostingsId());
        comment.setDeleted(false);
        comment.setLikeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount());
        comment.setUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        comment.setCreateTime(now);
        comment.setUpdateTime(now);

        primaryCommentRepository.save(comment);

        boolean isFirstComment = firstCommentService.recordFirstComment(comment.getPostingsId(), comment.getId());
        comment.setFirstComment(isFirstComment);
        commentCacheService.putPrimaryComment(comment);
        notificationSender.notifyPostComment(comment.getUserId(), post.getUserId(), post.getPostingsId(), comment.getId(), comment.getContent());
        commentMentionService.handleMentions(comment.getContent(), comment.getUserId(), comment.getId(), comment.getPostingsId());
        postingsService.adjustReplyCount(comment.getPostingsId(), 1);

        updateUserInterestVector(userId, post);

        return comment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeComment(Long commentId) {
        PrimaryComment existing = getExistingComment(commentId);
        LocalDateTime now = LocalDateTime.now();
        existing.setDeleted(true);
        existing.setUpdateTime(now);
        primaryCommentRepository.save(existing);
        // 替代 RabbitMQ 的 primary-comment-delete 队列：发 Spring 事件，
        // 由 @TransactionalEventListener(AFTER_COMMIT) 在事务提交后异步清理二级评论
        eventPublisher.publishEvent(new PrimaryCommentDeletedEvent(commentId));
        commentCacheService.evictPrimary(commentId);
        postingsService.adjustReplyCount(existing.getPostingsId(), -1);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrimaryComment> listByPostings(Long postingsId, int pageNum, int pageSize) {
        if (postingsId == null) {
            throw new BusinessException("postings id is required");
        }
        postingsService.getPost(postingsId);
        Specification<PrimaryComment> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("postingsId"), postingsId));
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<PrimaryComment> result = primaryCommentRepository.findAll(spec, pageable);
        Long firstCommentId = firstCommentService.getFirstCommentId(postingsId);
        Map<Long, Integer> repliesCountMap = loadSecondaryCountMap(result);
        Map<Long, Integer> likeCountMap = likeService.getLikeCountMap(
                LikeBizType.PRIMARY_COMMENT, collectPrimaryCommentIds(result));
        if (result != null && result.getContent() != null) {
            for (PrimaryComment record : result.getContent()) {
                if (record == null) {
                    continue;
                }
                boolean first = firstCommentId != null && firstCommentId.equals(record.getId());
                record.setFirstComment(first);
                record.setLikeCount(likeCountMap.getOrDefault(record.getId(), 0));
                record.setRepliesTotal(repliesCountMap.getOrDefault(record.getId(), 0));
            }
        }
        return result;
    }

    @Override
    public int toggleLike(Long commentId, Long userId, boolean like) {
        getExistingComment(commentId);
        LikeService.LikeToggleResult result = likeService.toggleLikeWithChange(LikeBizType.PRIMARY_COMMENT, commentId, userId, like);
        return result.count();
    }

    private void validateComment(PrimaryComment comment) {
        if (comment == null) {
            throw new BusinessException("comment is required");
        }
        if (comment.getPostingsId() == null) {
            throw new BusinessException("post id is required");
        }
        if (!StringUtils.hasText(comment.getContent())) {
            throw new BusinessException("comment content is required");
        }
        checkSensitiveWords(comment.getContent());
    }

    private void checkSensitiveWords(String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        Set<String> words = sensitiveWordService.findSensitiveWords(text);
        if (words.isEmpty()) {
            return;
        }
        String hitWord = pickMatchedSensitiveWord(text, words);
        throw new BusinessException("comment contains sensitive word: " + hitWord);
    }

    private String pickMatchedSensitiveWord(String text, Set<String> words) {
        if (words == null || words.isEmpty()) {
            return "";
        }
        if (!StringUtils.hasText(text)) {
            return words.iterator().next();
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        return words.stream()
                .filter(StringUtils::hasText)
                .sorted(Comparator
                        .comparingInt((String word) -> {
                            int index = lowerText.indexOf(word.toLowerCase(Locale.ROOT));
                            return index >= 0 ? index : Integer.MAX_VALUE;
                        })
                        .thenComparing(Comparator.comparingInt(String::length).reversed())
                        .thenComparing(Comparator.naturalOrder()))
                .findFirst()
                .orElseGet(() -> words.iterator().next());
    }

    private PrimaryComment getExistingComment(Long commentId) {
        if (commentId == null) {
            throw new BusinessException("comment id is required");
        }
        PrimaryComment comment = loadPrimaryComment(commentId);
        if (comment == null) {
            throw new BusinessException("comment not found");
        }
        return comment;
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

    private PrimaryComment loadPrimaryComment(Long commentId) {
        if (commentId == null) {
            return null;
        }
        PrimaryComment comment = commentCacheService.getPrimaryComment(commentId, () -> primaryCommentRepository.findById(commentId).orElse(null));
        if (comment == null || (comment.getDeleted() != null && comment.getDeleted())) {
            return null;
        }
        return comment;
    }

    private Map<Long, Integer> loadSecondaryCountMap(Page<PrimaryComment> page) {
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return Map.of();
        }
        List<Long> primaryIds = new ArrayList<>();
        for (PrimaryComment comment : page.getContent()) {
            if (comment != null && comment.getId() != null) {
                primaryIds.add(comment.getId());
            }
        }
        if (primaryIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = secondaryCommentRepository.countByPrimaryCommentIds(primaryIds);
        Map<Long, Integer> countMap = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            countMap.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return countMap;
    }

    private List<Long> collectPrimaryCommentIds(Page<PrimaryComment> page) {
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(page.getContent().size());
        for (PrimaryComment comment : page.getContent()) {
            if (comment != null && comment.getId() != null) {
                ids.add(comment.getId());
            }
        }
        return ids;
    }

    @Override
    public Page<PrimaryComment> getCommentList(Long postId, Long userId, int pageNum, int pageSize) {
        Specification<PrimaryComment> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (postId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("postingsId"), postId));
            }
            if (userId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("userId"), userId));
            }
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return primaryCommentRepository.findAll(spec, pageable);
    }

    private void updateUserInterestVector(Long userId, Postings post) {
        if (post == null || post.getType() == null) {
            return;
        }
        try {
            PostingType postingType = PostingType.fromString(post.getType());
            if (postingType != null) {
                userVectorBufferService.enqueue(userId, postingType);
            }
        } catch (Exception e) {
            log.warn("更新用户兴趣向量失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public PrimaryComment save(PrimaryComment comment) {
        return primaryCommentRepository.save(comment);
    }

    @Override
    public PrimaryComment getById(Long id) {
        return primaryCommentRepository.findById(id).orElse(null);
    }

    @Override
    public boolean updateById(PrimaryComment comment) {
        if (comment == null || comment.getId() == null) {
            return false;
        }
        primaryCommentRepository.save(comment);
        return true;
    }
}
