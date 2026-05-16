package com.example.demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring Cache + Caffeine 本地缓存配置
 * <p>
 * 缓存策略：
 * - postings: 帖子详情缓存，30 分钟过期，最大 10000 条
 * - likeCount: 点赞数缓存，5 分钟过期，最大 50000 条
 * - userLiked: 用户点赞状态缓存，10 分钟过期，最大 100000 条
 */

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 帖子详情缓存名称
     */
    public static final String CACHE_POSTINGS = "postings";

    /**
     * 点赞数缓存名称
     */
    public static final String CACHE_LIKE_COUNT = "likeCount";

    /**
     * 用户点赞状态缓存名称
     */
    public static final String CACHE_USER_LIKED = "userLiked";
    public static final String CACHE_USER_BY_EMAIL = "userByEmail";
    public static final String CACHE_MY_REPORTS = "myReports";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 注册不同配置的缓存，简化配置以兼容Native Image
        // Native Image下避免使用expireAfterAccess + expireAfterWrite + recordStats的组合
        // 因为Caffeine会动态生成大量不同的缓存实现类
        cacheManager.registerCustomCache(CACHE_POSTINGS,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(30))
                        .maximumSize(10000)
                        .build());

        cacheManager.registerCustomCache(CACHE_LIKE_COUNT,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .maximumSize(50000)
                        .build());

        cacheManager.registerCustomCache(CACHE_USER_LIKED,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(10))
                        .maximumSize(100000)
                        .build());

        cacheManager.registerCustomCache(CACHE_USER_BY_EMAIL,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(3))
                        .maximumSize(50000)
                        .build());

        cacheManager.registerCustomCache(CACHE_MY_REPORTS,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(1))
                        .maximumSize(20000)
                        .build());

        return cacheManager;
    }
}
