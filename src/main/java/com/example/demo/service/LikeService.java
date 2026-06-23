package com.example.demo.service;

import com.example.demo.config.CacheConfig;
import com.example.demo.entity.UserLike;
import com.example.demo.enums.LikeBizType;
import com.example.demo.repository.UserLikeRepository;
import com.example.demo.task.LikeBufferTrigger;
import com.example.demo.task.LikeChangeEvent;
import com.example.demo.task.LikeKeyConstants;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 点赞服务，使用Redis存储点赞状态，通过LikeBufferTrigger异步写入数据库
 */
@Service
public class LikeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserLikeRepository userLikeRepository;
    @Resource
    private CacheManager cacheManager;
    @Resource
    private LikeBufferTrigger likeBufferTrigger;

    /**
     * 点赞/取消点赞并返回点赞数
     */
    public LikeToggleResult toggleLikeWithChange(LikeBizType bizType, Long bizId, Long userId, boolean like) {
        return toggleLikeWithChange(bizType, bizId, userId, like, true);
    }

    /**
     * 点赞/取消点赞，可选择是否追踪排行榜
     */
    public LikeToggleResult toggleLikeWithChange(LikeBizType bizType, Long bizId, Long userId, boolean like, boolean trackRank) {
        String bizKey = LikeKeyConstants.setKey(bizType, bizId.toString());
        BoundSetOperations<String, String> ops = stringRedisTemplate.boundSetOps(bizKey);
        String userKey = userId.toString();

        boolean liked = Boolean.TRUE.equals(ops.isMember(userKey));
        if (!liked && hasLikeInDb(bizType, bizId, userId)) {
            liked = true;
        }

        boolean changed = false;
        boolean likedNow = liked;
        if (like && !liked) {
            changed = true;
            likedNow = true;
        } else if (!like && liked) {
            changed = true;
            likedNow = false;
        } else if (like && liked) {
            changed = true;
            likedNow = false;
        }

        int count;
        if (changed) {
            if (likedNow) {
                ops.add(userKey);
            } else {
                ops.remove(userKey);
            }
            updateUserLikeList(bizType, bizId, userId, likedNow);
            if (trackRank) {
                stringRedisTemplate.opsForZSet().add(
                        LikeKeyConstants.rankKey(bizType),
                        bizId.toString(),
                        System.currentTimeMillis());
            }
            LikeChangeEvent event = new LikeChangeEvent(bizType, bizId, userId, likedNow);
            likeBufferTrigger.enqueue(event);
            evictLikeCache(bizType, bizId, userId);
            Long size = ops.size();
            count = size == null ? 0 : size.intValue();
        } else {
            Long size = ops.size();
            count = size == null ? 0 : size.intValue();
        }

        return new LikeToggleResult(count, changed);
    }

    /**
     * 清除点赞缓存
     */
    private void evictLikeCache(LikeBizType bizType, Long bizId, Long userId) {
        String likeCountKey = bizType.getCode() + ":" + bizId;
        String userLikedKey = bizType.getCode() + ":" + bizId + ":" + userId;

        Cache likeCountCache = cacheManager.getCache(CacheConfig.CACHE_LIKE_COUNT);
        if (likeCountCache != null) {
            likeCountCache.evict(likeCountKey);
        }

        Cache userLikedCache = cacheManager.getCache(CacheConfig.CACHE_USER_LIKED);
        if (userLikedCache != null) {
            userLikedCache.evict(userLikedKey);
        }
    }

    /**
     * 获取点赞数，优先从缓存获取
     */
    @Cacheable(value = CacheConfig.CACHE_LIKE_COUNT, key = "#bizType + ':' + #bizId")
    public int getLikeCount(LikeBizType bizType, Long bizId) {
        String bizKey = LikeKeyConstants.setKey(bizType, bizId.toString());
        Long size = stringRedisTemplate.opsForSet().size(bizKey);
        return size == null ? 0 : size.intValue();
    }

    public Map<Long, Integer> getLikeCountMap(LikeBizType bizType, List<Long> bizIds) {
        if (bizType == null || bizIds == null || bizIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> effectiveIds = new ArrayList<>(bizIds.size());
        List<Object> pipelineResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long bizId : bizIds) {
                if (bizId == null) {
                    continue;
                }
                byte[] key = stringRedisTemplate.getStringSerializer()
                        .serialize(LikeKeyConstants.setKey(bizType, bizId.toString()));
                if (key != null) {
                    effectiveIds.add(bizId);
                    connection.sCard(key);
                }
            }
            return null;
        });

        Map<Long, Integer> countMap = new HashMap<>(effectiveIds.size());
        for (int i = 0; i < effectiveIds.size(); i++) {
            Object raw = i < pipelineResults.size() ? pipelineResults.get(i) : null;
            int count = raw instanceof Number number ? number.intValue() : 0;
            countMap.put(effectiveIds.get(i), count);
        }
        return countMap;
    }

    /**
     * 检查用户是否已点赞，优先查缓存
     */
    @Cacheable(value = CacheConfig.CACHE_USER_LIKED, key = "#bizType + ':' + #bizId + ':' + #userId")
    public boolean hasUserLiked(LikeBizType bizType, Long bizId, Long userId) {
        if (bizType == null || bizId == null || userId == null) {
            return false;
        }
        String bizKey = LikeKeyConstants.setKey(bizType, bizId.toString());
        Boolean liked = stringRedisTemplate.opsForSet().isMember(bizKey, userId.toString());
        if (Boolean.TRUE.equals(liked)) {
            return true;
        }
        return hasLikeInDb(bizType, bizId, userId);
    }

    /**
     * 点赞结果
     */
    public record LikeToggleResult(int count, boolean changed) {
    }

    /**
     * 检查数据库中是否存在点赞记录
     */
    private boolean hasLikeInDb(LikeBizType bizType, Long bizId, Long userId) {
        if (bizType == null || bizId == null || userId == null) {
            return false;
        }
        Specification<UserLike> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("bizType"), bizType.getCode()));
            predicate = cb.and(predicate, cb.equal(root.get("bizId"), bizId));
            predicate = cb.and(predicate, cb.equal(root.get("userId"), userId));
            predicate = cb.and(predicate, cb.equal(root.get("liked"), true));
            return predicate;
        };
        return userLikeRepository.count(spec) > 0;
    }

    /**
     * 更新用户点赞列表缓存
     */
    private void updateUserLikeList(LikeBizType bizType, Long bizId, Long userId, boolean liked) {
        String userLikeListKey = LikeKeyConstants.userLikeListKey(bizType, userId);
        if (liked) {
            stringRedisTemplate.opsForSet().add(userLikeListKey, bizId.toString());
        } else {
            stringRedisTemplate.opsForSet().remove(userLikeListKey, bizId.toString());
        }
    }

    /**
     * 获取用户点赞的对象ID列表
     *
     * 优先从Redis Set中获取用户点赞过的对象ID列表
     */
    public List<Long> getUserLikedBizIds(LikeBizType bizType, Long userId) {
        if (bizType == null || userId == null) {
            return Collections.emptyList();
        }

        List<Long> result = new ArrayList<>();

        String userLikeListKey = LikeKeyConstants.userLikeListKey(bizType, userId);
        Set<String> bizIds = stringRedisTemplate.opsForSet().members(userLikeListKey);

        if (bizIds != null && !bizIds.isEmpty()) {
            for (String bizIdStr : bizIds) {
                try {
                    result.add(Long.parseLong(bizIdStr));
                } catch (NumberFormatException ignored) {
                }
            }
            return result;
        }

        // Redis中没有，从数据库加载并写入Redis
        Specification<UserLike> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("userId"), userId));
            predicate = cb.and(predicate, cb.equal(root.get("bizType"), bizType.getCode()));
            predicate = cb.and(predicate, cb.equal(root.get("liked"), true));
            return predicate;
        };
        List<UserLike> dbLikes = userLikeRepository.findAll(spec);
        for (UserLike like : dbLikes) {
            if (like.getBizId() != null) {
                result.add(like.getBizId());
                stringRedisTemplate.opsForSet().add(userLikeListKey, like.getBizId().toString());
            }
        }

        return result;
    }
}
