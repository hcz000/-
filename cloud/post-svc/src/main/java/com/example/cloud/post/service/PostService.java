package com.example.cloud.post.service;

import com.example.cloud.common.api.post.PostCreatedEvent;
import com.example.cloud.common.dto.CursorPage;
import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.post.entity.Planet;
import com.example.cloud.post.entity.Postings;
import com.example.cloud.post.enums.LikeBizType;
import com.example.cloud.post.repository.PostingsRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 帖子服务。
 * <p>
 * 与单体 PostingsServiceImpl 相比的简化：
 * <ul>
 *   <li>去掉 @Cacheable / 敏感词检查 / NotificationSender 调用</li>
 *   <li>「入候选池」和「热度榜」不在 createPost 内同步调用，
 *       而是通过 Outbox 事件让 push-svc 异步消费处理（已实现）</li>
 *   <li>JPA 查询去掉 @EntityGraph 关联（User 不在本服务，详情查询调用方走 Feign）</li>
 * </ul>
 */
@Slf4j
@Service
public class PostService {

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private LikeService likeService;

    @Resource
    private PlanetService planetService;

    @Resource
    private OutboxEventService outboxEventService;

    // ===== 创建 =====

    /**
     * 创建帖子，同事务写 outbox 事件（最终一致地通知 push-svc 入候选池）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Postings createPostWithOutbox(Postings post) {
        if (post == null || post.getUserId() == null) {
            throw new BusinessException("userId is required");
        }
        validate(post);

        Planet planet = planetService.getPlanet(post.getPlanetId());
        if (Boolean.TRUE.equals(planet.getDeleted())) throw new BusinessException("planet is deleted");
        if (!planetService.isMember(post.getPlanetId(), post.getUserId())) {
            throw new BusinessException("not joined planet");
        }

        LocalDateTime now = LocalDateTime.now();
        post.setStatus(post.getStatus() == null ? 1 : post.getStatus());
        post.setAuditStatus(post.getAuditStatus() == null ? 1 : post.getAuditStatus());
        post.setReplyCount(post.getReplyCount() == null ? 0 : post.getReplyCount());
        post.setLikeCount(post.getLikeCount() == null ? 0 : post.getLikeCount());
        post.setDeleted(false);
        post.setCreateTime(now);
        post.setUpdateTime(now);
        Postings saved = postingsRepository.save(post);

        // 同事务写 outbox，由调度器异步发到 MQ
        outboxEventService.saveCreated(new PostCreatedEvent(
                UUID.randomUUID().toString(),
                saved.getPostingsId(), saved.getUserId(), saved.getPlanetId(),
                saved.getType(), now));

        log.info("[post] created postId={}, userId={}", saved.getPostingsId(), saved.getUserId());
        return saved;
    }

    // ===== 查询 =====

    public Postings getPost(Long postingsId) {
        return postingsRepository.findById(postingsId)
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .orElseThrow(() -> new BusinessException("帖子不存在"));
    }

    /**
     * 游标分页：按 createTime + postingsId 倒序，安全地翻页。
     */
    public CursorPage<Postings> listByPlanetIdCursor(Long planetId, LocalDateTime cursor, Long lastId, int size) {
        if (planetId == null) throw new BusinessException("planetId is required");
        Specification<Postings> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("deleted"), false);
            p = cb.and(p, cb.equal(root.get("status"), 1));
            p = cb.and(p, cb.equal(root.get("auditStatus"), 1));
            p = cb.and(p, cb.equal(root.get("planetId"), planetId));
            if (cursor != null && lastId != null) {
                Predicate before = cb.lessThan(root.get("createTime"), cursor);
                Predicate sameTimeButOlder = cb.and(
                        cb.equal(root.get("createTime"), cursor),
                        cb.lessThan(root.get("postingsId"), lastId));
                p = cb.and(p, cb.or(before, sameTimeButOlder));
            }
            return p;
        };
        Page<Postings> page = postingsRepository.findAll(spec,
                PageRequest.of(0, size + 1,
                        Sort.by(Sort.Direction.DESC, "createTime").and(Sort.by(Sort.Direction.DESC, "postingsId"))));
        List<Postings> records = new ArrayList<>(page.getContent());
        boolean hasMore = records.size() > size;
        if (hasMore) records = records.subList(0, size);
        return CursorPage.of(records, hasMore);
    }

    // ===== 更新 / 删除 =====

    @Transactional(rollbackFor = Exception.class)
    public Postings updatePost(Postings post) {
        if (post == null || post.getPostingsId() == null) throw new BusinessException("postingsId is required");
        Postings existing = postingsRepository.findById(post.getPostingsId())
                .orElseThrow(() -> new BusinessException("帖子不存在"));
        if (post.getTitle() != null) existing.setTitle(post.getTitle());
        if (post.getContent() != null) existing.setContent(post.getContent());
        if (post.getImages() != null) existing.setImages(post.getImages());
        if (post.getType() != null) existing.setType(post.getType());
        existing.setUpdateTime(LocalDateTime.now());
        return postingsRepository.save(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removePost(Long postingsId) {
        postingsRepository.findById(postingsId).ifPresent(post -> {
            post.setDeleted(true);
            post.setUpdateTime(LocalDateTime.now());
            postingsRepository.save(post);
        });
    }

    /**
     * 软删除某用户名下的所有帖子（Seata AT 分支事务）。
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

    // ===== 点赞 =====

    public int togglePostLike(Long postingsId, Long userId, boolean like) {
        int count = likeService.toggleLike(LikeBizType.POST, postingsId, userId, like);
        postingsRepository.findById(postingsId).ifPresent(post -> {
            post.setLikeCount(count);
            postingsRepository.save(post);
        });
        return count;
    }

    // ===== 搜索 =====

    public Page<Postings> searchPosts(String keyword, Long planetId, int pageNum, int pageSize) {
        if (!StringUtils.hasText(keyword)) return Page.empty();
        long offset = (long) (pageNum - 1) * pageSize;
        List<Postings> records = postingsRepository.searchByFullText(keyword, pageSize, offset);
        if (planetId != null) {
            records = records.stream().filter(p -> planetId.equals(p.getPlanetId())).sorted(
                    Comparator.comparing(Postings::getCreateTime).reversed()).toList();
        }
        Long total = postingsRepository.countSearchResults(keyword);
        return new PageImpl<>(records, PageRequest.of(pageNum - 1, pageSize), total == null ? 0L : total);
    }

    public Page<Postings> listLikedPosts(Long userId, int pageNum, int pageSize) {
        // 简化：直接走 user_like 表逻辑略复杂，留 TODO
        log.warn("[post] listLikedPosts simplified, returning empty for now, userId={}", userId);
        return Page.empty(PageRequest.of(pageNum - 1, pageSize));
    }

    public void adjustReplyCount(Long postingsId, int delta) {
        postingsRepository.findById(postingsId).ifPresent(post -> {
            post.setReplyCount(Math.max(0, (post.getReplyCount() == null ? 0 : post.getReplyCount()) + delta));
            post.setUpdateTime(LocalDateTime.now());
            postingsRepository.save(post);
        });
    }

    // ===== Admin 后台辅助 =====

    public Postings getById(Long id) {
        return id == null ? null : postingsRepository.findById(id).orElse(null);
    }

    public boolean updateById(Postings post) {
        if (post == null || post.getPostingsId() == null) return false;
        post.setUpdateTime(LocalDateTime.now());
        postingsRepository.save(post);
        return true;
    }

    /**
     * 多条件查询（admin 用）。
     */
    public org.springframework.data.domain.Page<Postings> getPostList(
            Long planetId, Long userId, String keyword, int pageNum, int pageSize) {
        Specification<Postings> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("deleted"), false);
            if (planetId != null) p = cb.and(p, cb.equal(root.get("planetId"), planetId));
            if (userId != null) p = cb.and(p, cb.equal(root.get("userId"), userId));
            if (StringUtils.hasText(keyword)) {
                Predicate titleLike = cb.like(root.get("title"), "%" + keyword + "%");
                Predicate contentLike = cb.like(root.get("content"), "%" + keyword + "%");
                p = cb.and(p, cb.or(titleLike, contentLike));
            }
            return p;
        };
        return postingsRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createTime")));
    }

    // ===== Validation =====

    private void validate(Postings post) {
        if (post.getPlanetId() == null) throw new BusinessException("planetId is required");
        if (!StringUtils.hasText(post.getTitle()) && !StringUtils.hasText(post.getContent())) {
            throw new BusinessException("标题和内容至少填写一项");
        }
    }
}
