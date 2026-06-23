package com.example.demo.service;

import com.example.demo.config.CacheConfig;
import com.example.demo.entity.Postings;
import com.example.demo.enums.LikeBizType;
import com.example.demo.repository.PostingsRepository;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HotRankService {

    private static final String HOT_RANK_KEY = "hot:rank:posts";
    private static final int DEFAULT_LIMIT = 20;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private LikeService likeService;

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CandidatePoolService candidatePoolService;

    public void refreshPost(Postings post) {
        if (post == null || post.getPostingsId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(post.getDeleted())
                || post.getStatus() == null || post.getStatus() != 1
                || post.getAuditStatus() == null || post.getAuditStatus() != 1) {
            remove(post.getPostingsId());
            return;
        }
        double score = calculateScore(post);
        stringRedisTemplate.opsForZSet().add(HOT_RANK_KEY, post.getPostingsId().toString(), score);
        // 预热帖子详情到 Redis，供 hotPush / 其他推送策略直接命中
        candidatePoolService.cachePostDetail(post);
        evictHotCache();
    }

    public void touchPost(Long postId) {
        if (postId == null) {
            return;
        }
        postingsRepository.findById(postId).ifPresent(this::refreshPost);
    }

    public void remove(Long postId) {
        if (postId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(HOT_RANK_KEY, postId.toString());
        evictHotCache();
    }

    public List<Long> topIds(int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : limit;
        Set<String> raw = stringRedisTemplate.opsForZSet().reverseRange(HOT_RANK_KEY, 0, size - 1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>(raw.size());
        for (String id : raw) {
            try {
                result.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Postings> topPosts(int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : limit;
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_HOT_POSTS);
        String cacheKey = "top:" + size;
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null && wrapper.get() instanceof List<?> list) {
                return (List<Postings>) list;
            }
        }

        List<Long> ids = topIds(size);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Postings> postMap = postingsRepository.findAllById(ids).stream()
                .filter(p -> p.getPostingsId() != null)
                .collect(java.util.stream.Collectors.toMap(Postings::getPostingsId, p -> p, (a, b) -> a));
        List<Postings> posts = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Postings post = postMap.get(id);
            if (post != null) {
                posts.add(post);
            }
        }
        // 回填 Redis detail 缓存，供 hotPush 的 materializeByOrderedIds 命中
        candidatePoolService.batchCachePostDetail(posts);
        if (cache != null) {
            cache.put(cacheKey, posts);
        }
        return posts;
    }

    public void rebuildRecent(int limit) {
        int size = limit <= 0 ? 500 : limit;
        List<Long> ids = postingsRepository.findRecentLiveIds(size);
        if (ids.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>(ids);
        List<Postings> livePosts = postingsRepository.findAllById(seen);
        // Pipeline 批量预热帖子详情到 Redis（一次写完整，不再逐条重复写）
        candidatePoolService.batchCachePostDetail(livePosts);
        for (Postings post : livePosts) {
            updateScoreOnly(post);
        }
        evictHotCache();
        log.info("[hot] rebuildRecent done, posts={}, detail cached", livePosts.size());
    }

    /**
     * 仅更新 ZSet 分数（不写 detail 缓存、不清 Caffeine），供 rebuildRecent 批量场景使用
     */
    private void updateScoreOnly(Postings post) {
        if (post == null || post.getPostingsId() == null) return;
        if (Boolean.TRUE.equals(post.getDeleted())
                || post.getStatus() == null || post.getStatus() != 1
                || post.getAuditStatus() == null || post.getAuditStatus() != 1) {
            stringRedisTemplate.opsForZSet().remove(HOT_RANK_KEY, post.getPostingsId().toString());
            return;
        }
        double score = calculateScore(post);
        stringRedisTemplate.opsForZSet().add(HOT_RANK_KEY, post.getPostingsId().toString(), score);
    }

    private double calculateScore(Postings post) {
        double likeWeight = systemConfigService.getDouble(SystemConfigService.HOT_LIKE_WEIGHT, 3.0);
        double replyWeight = systemConfigService.getDouble(SystemConfigService.HOT_REPLY_WEIGHT, 5.0);
        double halfLifeHours = systemConfigService.getDouble(SystemConfigService.HOT_HALF_LIFE_HOURS, 24.0);
        int likeCount = likeService.getLikeCount(LikeBizType.POST, post.getPostingsId());
        int replyCount = post.getReplyCount() == null ? 0 : post.getReplyCount();
        double base = likeCount * likeWeight + replyCount * replyWeight + 1;
        LocalDateTime createTime = post.getCreateTime() == null ? LocalDateTime.now() : post.getCreateTime();
        long ageMinutes = Math.max(0, Duration.between(createTime, LocalDateTime.now()).toMinutes());
        double halfLifeMinutes = Math.max(60, halfLifeHours * 60);
        double decay = Math.pow(0.5, ageMinutes / halfLifeMinutes);
        return base * decay;
    }

    private void evictHotCache() {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_HOT_POSTS);
        if (cache != null) {
            cache.clear();
        }
    }
}
