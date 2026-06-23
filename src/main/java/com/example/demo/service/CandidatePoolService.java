package com.example.demo.service;

import com.example.demo.entity.Postings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 候选池服务：帖子发布时入池、删除时出池
 * <p>
 * Key 按日期分片，利用 Redis TTL 自然过期，无需定时重建。
 * <ul>
 *   <li>日榜：push:posts:daily:{yyyy-MM-dd}，TTL 1 天</li>
 *   <li>周榜：push:posts:weekly:{yyyy-MM-dd}，TTL 7 天</li>
 *   <li>类型池：push:posts:type:{typeName}:{yyyy-MM-dd}，TTL 7 天</li>
 * </ul>
 */
@Slf4j
@Service
public class CandidatePoolService {

    private static final String DAILY_PREFIX = "push:posts:daily:";
    private static final String WEEKLY_PREFIX = "push:posts:weekly:";
    private static final String TYPE_PREFIX = "push:posts:type:";

    private static final long DAILY_TTL_DAYS = 1;
    private static final long WEEKLY_TTL_DAYS = 7;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DETAIL_PREFIX = "push:detail:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ===== 入池 / 出池 =====

    /**
     * 帖子发布（审核通过 + 状态正常）时加入三个候选池 + 缓存详情
     */
    public void addToPools(Postings post) {
        if (post == null || post.getPostingsId() == null) return;
        Long postId = post.getPostingsId();
        String today = todayStr();
        String idStr = postId.toString();

        addToSet(DAILY_PREFIX + today, idStr, DAILY_TTL_DAYS);
        addToSet(WEEKLY_PREFIX + today, idStr, WEEKLY_TTL_DAYS);
        if (post.getType() != null) {
            addToSet(TYPE_PREFIX + post.getType() + ":" + today, idStr, WEEKLY_TTL_DAYS);
        }
        cachePostDetail(post);
        log.debug("[pool] added postId={}, type={}", postId, post.getType());
    }

    /**
     * 帖子删除 / 审核拒绝 / 下架时从三个候选池移除 + 删除详情缓存
     */
    public void removeFromPools(Long postId, String type) {
        if (postId == null) return;
        String today = todayStr();
        String idStr = postId.toString();

        stringRedisTemplate.opsForSet().remove(DAILY_PREFIX + today, idStr);
        stringRedisTemplate.opsForSet().remove(WEEKLY_PREFIX + today, idStr);
        if (type != null) {
            stringRedisTemplate.opsForSet().remove(TYPE_PREFIX + type + ":" + today, idStr);
        }
        // 删除详情缓存
        stringRedisTemplate.delete(DETAIL_PREFIX + idStr);
        log.debug("[pool] removed postId={}, type={}", postId, type);
    }

    // ===== 读取 =====

    /**
     * 从今日日榜随机抽取
     */
    public List<Long> getRandomFromDaily(int count) {
        return randomFromSet(DAILY_PREFIX + todayStr(), count);
    }

    /**
     * 从今日周榜随机抽取（key 包含最近 7 天的帖子）
     */
    public List<Long> getRandomFromWeekly(int count) {
        return randomFromSet(WEEKLY_PREFIX + todayStr(), count);
    }

    /**
     * 从最近 days 天的类型池中 Pipeline 批量读取全部 ID
     */
    public List<Long> getIdsByType(String type, int days) {
        LocalDate today = LocalDate.now();
        List<String> keys = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            keys.add(TYPE_PREFIX + type + ":" + today.minusDays(i).format(DATE_FMT));
        }
        return batchReadSets(keys);
    }

    // ===== 内部方法 =====

    private void addToSet(String key, String member, long ttlDays) {
        stringRedisTemplate.opsForSet().add(key, member);
        stringRedisTemplate.expire(key, ttlDays, TimeUnit.DAYS);
    }

    private List<Long> randomFromSet(String key, int count) {
        Set<String> members = stringRedisTemplate.opsForSet().distinctRandomMembers(key, count);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        return members.stream().map(Long::parseLong).toList();
    }

    /**
     * Pipeline 批量读取多个 Set 的全部成员，合并返回
     */
    private List<Long> batchReadSets(List<String> keys) {
        List<Object> results = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                byte[] keyBytes = stringRedisTemplate.getStringSerializer().serialize(key);
                if (keyBytes != null) {
                    connection.setCommands().sMembers(keyBytes);
                }
            }
            return null;
        });

        List<Long> allIds = new ArrayList<>();
        for (Object result : results) {
            if (result instanceof Set<?> set) {
                for (Object item : set) {
                    if (item != null) {
                        try {
                            allIds.add(Long.parseLong(item.toString()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return allIds;
    }

    private String todayStr() {
        return LocalDate.now().format(DATE_FMT);
    }

    // ===== 帖子详情缓存 =====

    /**
     * 将帖子详情写入 Redis（TTL 7天，与周榜一致）
     */
    public void cachePostDetail(Postings post) {
        if (post == null || post.getPostingsId() == null) return;
        try {
            String json = MAPPER.writeValueAsString(post);
            stringRedisTemplate.opsForValue().set(
                    DETAIL_PREFIX + post.getPostingsId(),
                    json,
                    WEEKLY_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            log.warn("[pool] cache post detail failed, postId={}", post.getPostingsId(), e);
        }
    }

    /**
     * Pipeline 批量预热帖子详情到 Redis（TTL 7天）
     */
    public void batchCachePostDetail(java.util.Collection<? extends Postings> posts) {
        if (posts == null || posts.isEmpty()) return;
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Postings post : posts) {
                if (post == null || post.getPostingsId() == null) continue;
                try {
                    String json = MAPPER.writeValueAsString(post);
                    byte[] key = stringRedisTemplate.getStringSerializer()
                            .serialize(DETAIL_PREFIX + post.getPostingsId());
                    byte[] val = stringRedisTemplate.getStringSerializer().serialize(json);
                    if (key != null && val != null) {
                        connection.stringCommands().set(
                                key, val,
                                Expiration.from(WEEKLY_TTL_DAYS, TimeUnit.DAYS),
                                SetOption.upsert());
                    }
                } catch (JsonProcessingException e) {
                    log.warn("[pool] batch cache post detail failed, postId={}", post.getPostingsId(), e);
                }
            }
            return null;
        });
        log.debug("[pool] batch cached {} post details", posts.size());
    }
}
