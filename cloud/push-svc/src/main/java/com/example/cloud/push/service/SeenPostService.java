package com.example.cloud.push.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 推送曝光记录中心：用 Redis Set 保存每个用户最近 24h 已经看过的帖子 id。
 * <p>
 * 推送服务在挑选候选时把这个集合作为排除集，使得用户「看完 10 条刷新」
 * 时能拿到没看过的新内容；候选耗尽则返回空，由前端展示「暂无更多」。
 * <p>
 * Key 格式：{@code push:seen:user:{userId}}，每次写入续期到 24h。
 */
@Slf4j
@Service
public class SeenPostService {

    private static final String SEEN_KEY_PREFIX = "push:seen:user:";
    private static final long SEEN_TTL_HOURS = 24;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Set<Long> getSeen(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> members = stringRedisTemplate.opsForSet().members(seenKey(userId));
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new HashSet<>(members.size());
        for (String m : members) {
            if (m == null || m.isBlank()) continue;
            try {
                ids.add(Long.parseLong(m));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    public void markSeen(Long userId, Collection<Long> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return;
        }
        String key = seenKey(userId);
        String[] members = postIds.stream()
                .filter(id -> id != null)
                .map(String::valueOf)
                .toArray(String[]::new);
        if (members.length == 0) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().add(key, members);
            stringRedisTemplate.expire(key, SEEN_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[seen] markSeen failed userId={}, size={}", userId, members.length, e);
        }
    }

    public void resetSeen(Long userId) {
        if (userId == null) return;
        try {
            stringRedisTemplate.delete(seenKey(userId));
        } catch (Exception e) {
            log.warn("[seen] resetSeen failed userId={}", userId, e);
        }
    }

    private String seenKey(Long userId) {
        return SEEN_KEY_PREFIX + userId;
    }
}
