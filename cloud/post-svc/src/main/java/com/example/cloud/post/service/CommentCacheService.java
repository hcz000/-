package com.example.cloud.post.service;

import com.example.cloud.post.entity.PrimaryComment;
import com.example.cloud.post.entity.SecondaryComment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 评论缓存服务（Redis 字符串缓存，5 分钟 TTL）。
 * <p>
 * 用 Jackson + FAIL_ON_UNKNOWN_PROPERTIES=false：实体新增字段后旧 Redis JSON 仍可反序列化。
 */
@Service
public class CommentCacheService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PRIMARY_PREFIX = "comment:primary:";
    private static final String SECONDARY_PREFIX = "comment:secondary:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PrimaryComment getPrimaryComment(Long id, Supplier<PrimaryComment> loader) {
        return get(PRIMARY_PREFIX + id, PrimaryComment.class, loader);
    }

    public void putPrimaryComment(PrimaryComment comment) {
        put(PRIMARY_PREFIX + comment.getId(), comment);
    }

    public void evictPrimary(Long id) {
        stringRedisTemplate.delete(PRIMARY_PREFIX + id);
    }

    public SecondaryComment getSecondaryComment(Long id, Supplier<SecondaryComment> loader) {
        return get(SECONDARY_PREFIX + id, SecondaryComment.class, loader);
    }

    public void putSecondaryComment(SecondaryComment comment) {
        put(SECONDARY_PREFIX + comment.getId(), comment);
    }

    public void evictSecondary(Long id) {
        stringRedisTemplate.delete(SECONDARY_PREFIX + id);
    }

    private <T> T get(String key, Class<T> type, Supplier<T> loader) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                return objectMapper.readValue(json, type);
            } catch (JsonProcessingException ignored) {
            }
        }
        T value = loader.get();
        if (value != null) put(key, value);
        return value;
    }

    private void put(String key, Object value) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), TTL);
        } catch (JsonProcessingException ignored) {
        }
    }
}
