package com.example.cloud.post.service;

import com.example.cloud.post.entity.UserLike;
import com.example.cloud.post.enums.LikeBizType;
import com.example.cloud.post.repository.UserLikeRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 点赞服务（简化版）。
 * <p>
 * 单体版有 LikeBufferTrigger 异步批量入库 + Caffeine 二级缓存 + Redis ZSet 排行榜，
 * 这里简化为「Redis Set 即时维护 + 写 DB 即时同步」，没有批处理。性能足够 demo。
 *
 * <h3>数据结构</h3>
 * <ul>
 *   <li>Redis Set: {@code like:{bizType.code}:{bizId}} —— 存所有点赞过的 userId</li>
 *   <li>DB user_like 表：作为持久层，重启 Redis 后用于重建集合（这里没实现冷启动重建，简化）</li>
 * </ul>
 */
@Slf4j
@Service
public class LikeService {

    private static final String LIKE_SET_PREFIX = "like:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserLikeRepository userLikeRepository;

    /**
     * 点赞/取消点赞，返回最新点赞数。
     */
    @Transactional(rollbackFor = Exception.class)
    public int toggleLike(LikeBizType bizType, Long bizId, Long userId, boolean like) {
        if (bizType == null || bizId == null || userId == null) {
            throw new IllegalArgumentException("bizType / bizId / userId 不能为空");
        }
        String setKey = setKey(bizType, bizId);
        BoundSetOperations<String, String> ops = stringRedisTemplate.boundSetOps(setKey);
        String userKey = userId.toString();

        boolean wasLiked = Boolean.TRUE.equals(ops.isMember(userKey));
        if (like && !wasLiked) {
            ops.add(userKey);
            upsertUserLikeRow(bizType, bizId, userId, true);
        } else if (!like && wasLiked) {
            ops.remove(userKey);
            upsertUserLikeRow(bizType, bizId, userId, false);
        }
        Long size = ops.size();
        return size == null ? 0 : size.intValue();
    }

    public boolean hasUserLiked(LikeBizType bizType, Long bizId, Long userId) {
        if (bizType == null || bizId == null || userId == null) return false;
        return Boolean.TRUE.equals(
                stringRedisTemplate.boundSetOps(setKey(bizType, bizId)).isMember(userId.toString()));
    }

    public int getLikeCount(LikeBizType bizType, Long bizId) {
        if (bizType == null || bizId == null) return 0;
        Long size = stringRedisTemplate.boundSetOps(setKey(bizType, bizId)).size();
        return size == null ? 0 : size.intValue();
    }

    private String setKey(LikeBizType bizType, Long bizId) {
        return LIKE_SET_PREFIX + bizType.getCode() + ":" + bizId;
    }

    /**
     * 同步 user_like 表（用于持久化和后续 Redis 重建）。
     * 简化做法：每次点赞/取消都直接写一行；生产可以走异步批处理减压。
     */
    private void upsertUserLikeRow(LikeBizType bizType, Long bizId, Long userId, boolean liked) {
        try {
            Specification<UserLike> spec = (root, query, cb) -> {
                Predicate p = cb.conjunction();
                p = cb.and(p, cb.equal(root.get("bizType"), bizType.getCode()));
                p = cb.and(p, cb.equal(root.get("bizId"), bizId));
                p = cb.and(p, cb.equal(root.get("userId"), userId));
                return p;
            };
            UserLike row = userLikeRepository.findOne(spec).orElseGet(UserLike::new);
            LocalDateTime now = LocalDateTime.now();
            row.setBizType(bizType.getCode());
            row.setBizId(bizId);
            row.setUserId(userId);
            row.setLiked(liked);
            if (row.getCreateTime() == null) row.setCreateTime(now);
            row.setUpdateTime(now);
            userLikeRepository.save(row);
        } catch (Exception e) {
            log.warn("[like] persist user_like failed, bizType={}, bizId={}, userId={}",
                    bizType, bizId, userId, e);
        }
    }
}
