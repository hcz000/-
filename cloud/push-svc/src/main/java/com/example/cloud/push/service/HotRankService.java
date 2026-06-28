package com.example.cloud.push.service;

import com.example.cloud.push.entity.Postings;
import com.example.cloud.push.repository.PostingsRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 热度榜服务（push-svc 真实实现）。
 * <p>
 * 与单体相比的简化：
 * <ul>
 *   <li>{@code likeCount} 直接读 postings.like_count 列（不再调 LikeService）；
 *       push-svc 不持有 user_like 表写权限，没必要走 LikeService</li>
 *   <li>权重/半衰期来自 application.yml（hot.like-weight 等），不再从 SystemConfigService 查 DB</li>
 *   <li>去掉 Caffeine 二级缓存（topPosts），生产可以加回</li>
 * </ul>
 *
 * <h3>数据结构</h3>
 * Redis ZSet {@code hot:rank:posts}: member = postingsId, score = 热度分。
 * 排序 reverseRange 取 top N。
 *
 * <h3>分数计算</h3>
 * <pre>
 *   base = likeCount * likeWeight + replyCount * replyWeight + 1
 *   ageMinutes = now - createTime
 *   decay = 0.5 ^ (ageMinutes / (halfLifeHours * 60))
 *   score = base * decay
 * </pre>
 *
 * <h3>触发点</h3>
 * 由 {@link com.example.cloud.push.mq.PostEventConsumer} 在收到 PostCreatedEvent 时调用
 * {@link #refreshPost} 维护榜单；定时任务 {@link #rebuildRecent} 兜底重建。
 */
@Slf4j
@Service
public class HotRankService {

    private static final String HOT_RANK_KEY = "hot:rank:posts";
    private static final int DEFAULT_LIMIT = 20;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PostingsRepository postingsRepository;

    @Value("${app.hot.like-weight:3.0}")
    private double likeWeight;

    @Value("${app.hot.reply-weight:5.0}")
    private double replyWeight;

    @Value("${app.hot.half-life-hours:24.0}")
    private double halfLifeHours;

    public void refreshPost(Postings post) {
        if (post == null || post.getPostingsId() == null) return;
        if (Boolean.TRUE.equals(post.getDeleted())
                || post.getStatus() == null || post.getStatus() != 1
                || post.getAuditStatus() == null || post.getAuditStatus() != 1) {
            remove(post.getPostingsId());
            return;
        }
        double score = calculateScore(post);
        stringRedisTemplate.opsForZSet().add(HOT_RANK_KEY, post.getPostingsId().toString(), score);
        log.debug("[hot] refresh postId={}, score={}", post.getPostingsId(), score);
    }

    public void touchPost(Long postId) {
        if (postId == null) return;
        postingsRepository.findById(postId).ifPresent(this::refreshPost);
    }

    public void remove(Long postId) {
        if (postId == null) return;
        stringRedisTemplate.opsForZSet().remove(HOT_RANK_KEY, postId.toString());
    }

    public List<Long> topIds(int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : limit;
        Set<String> raw = stringRedisTemplate.opsForZSet().reverseRange(HOT_RANK_KEY, 0, size - 1);
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<Long> result = new ArrayList<>(raw.size());
        for (String id : raw) {
            try {
                result.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    /**
     * 兜底：扫描最近 N 条有效帖子，重建整个 ZSet。
     * 触发场景：
     * <ul>
     *   <li>启动后 zset 为空</li>
     *   <li>定时任务（生产可加 @Scheduled）</li>
     * </ul>
     */
    public void rebuildRecent(int limit) {
        int size = limit <= 0 ? 500 : limit;
        List<Long> ids = postingsRepository.findRecentLiveIds(size);
        if (ids.isEmpty()) {
            log.debug("[hot] rebuildRecent: no posts found");
            return;
        }
        List<Postings> posts = postingsRepository.findAllById(new HashSet<>(ids));
        for (Postings post : posts) {
            if (post == null || post.getPostingsId() == null) continue;
            if (Boolean.TRUE.equals(post.getDeleted())
                    || post.getStatus() == null || post.getStatus() != 1
                    || post.getAuditStatus() == null || post.getAuditStatus() != 1) {
                stringRedisTemplate.opsForZSet().remove(HOT_RANK_KEY, post.getPostingsId().toString());
                continue;
            }
            double score = calculateScore(post);
            stringRedisTemplate.opsForZSet().add(HOT_RANK_KEY, post.getPostingsId().toString(), score);
        }
        log.info("[hot] rebuildRecent done, posts={}", posts.size());
    }

    private double calculateScore(Postings post) {
        int likeCount = post.getLikeCount() == null ? 0 : post.getLikeCount();
        int replyCount = post.getReplyCount() == null ? 0 : post.getReplyCount();
        double base = likeCount * likeWeight + replyCount * replyWeight + 1;
        LocalDateTime createTime = post.getCreateTime() == null ? LocalDateTime.now() : post.getCreateTime();
        long ageMinutes = Math.max(0, Duration.between(createTime, LocalDateTime.now()).toMinutes());
        double halfLifeMinutes = Math.max(60, halfLifeHours * 60);
        double decay = Math.pow(0.5, ageMinutes / halfLifeMinutes);
        return base * decay;
    }
}
