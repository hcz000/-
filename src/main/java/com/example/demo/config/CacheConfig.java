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
    public static final String CACHE_USER_INTEREST_RATIOS = "userInterestRatios";
    public static final String CACHE_SYSTEM_CONFIG = "systemConfig";
    public static final String CACHE_HOT_POSTS = "hotPosts";
    
    /**
     * 星球信息缓存 (高频查询,变化少)
     */
    public static final String CACHE_PLANET = "planet";
    
    /**
     * 用户ID查询缓存 (补充email缓存)
     */
    public static final String CACHE_USER_BY_ID = "userById";
    
    /**
     * 通知未读数缓存 (高频查询,短期有效)
     */
    public static final String CACHE_UNREAD_COUNT = "unreadCount";
    
    /**
     * 用户星球成员关系缓存 (频繁检查)
     */
    public static final String CACHE_PLANET_MEMBER = "planetMember";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
    
        // 注册不同配置的缓存,简化配置以兼容Native Image
        // Native Image下避免使用expireAfterAccess + expireAfterWrite + recordStats的组合
        // 因为Caffeine会动态生成大量不同的缓存实现类
            
        // 帖子详情缓存: 30分钟过期,最大10000条
        // 优化: 添加recordStats用于监控缓存命中率
        cacheManager.registerCustomCache(CACHE_POSTINGS,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(30))
                        .maximumSize(10000)
                        .recordStats() // 开启统计,便于监控
                        .build());
    
        // 点赞数缓存: 5分钟过期,最大50000条
        // 优化: 高频访问数据,使用refreshAfterWrite避免缓存击穿
        cacheManager.registerCustomCache(CACHE_LIKE_COUNT,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .maximumSize(50000)
                        .recordStats()
                        .build());
    
        // 用户点赞状态缓存: 10分钟过期,最大100000条
        // 优化: 大容量缓存,使用弱引用值减少内存占用
        cacheManager.registerCustomCache(CACHE_USER_LIKED,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(10))
                        .maximumSize(100000)
                        .recordStats()
                        .build());
    
        // 用户邮箱查询缓存: 5分钟过期,最大50000条
        // 优化: 短期缓存,避免重复查询数据库
        cacheManager.registerCustomCache(CACHE_USER_BY_EMAIL,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .maximumSize(50000)
                        .recordStats()
                        .build());
    
        // 我的举报缓存: 5分钟过期,最大20000条
        // 优化: 短期缓存,数据变化频繁
        cacheManager.registerCustomCache(CACHE_MY_REPORTS,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .maximumSize(20000)
                        .recordStats()
                        .build());

        // 用户兴趣比缓存: 2分钟过期,最大50000条
        cacheManager.registerCustomCache(CACHE_USER_INTEREST_RATIOS,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(2))
                        .maximumSize(50000)
                        .recordStats()
                        .build());

        // 系统配置缓存: 5分钟过期,最大1000条
        cacheManager.registerCustomCache(CACHE_SYSTEM_CONFIG,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .maximumSize(1000)
                        .recordStats()
                        .build());

        // 热帖缓存: 30秒过期,最大2000条
        cacheManager.registerCustomCache(CACHE_HOT_POSTS,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .maximumSize(2000)
                        .recordStats()
                        .build());
        
        // 星球信息缓存: 30分钟过期,最大5000条
        // 优化: 高频查询,变化少,适合长期缓存
        cacheManager.registerCustomCache(CACHE_PLANET,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(30))
                        .maximumSize(5000)
                        .recordStats()
                        .build());
        
        // 用户ID查询缓存: 5分钟过期,最大50000条
        // 优化: 补充email缓存,避免重复查询
        cacheManager.registerCustomCache(CACHE_USER_BY_ID,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .maximumSize(50000)
                        .recordStats()
                        .build());
        
        // 通知未读数缓存: 30秒过期,最大10000条
        // 优化: 高频查询,短期有效,避免频繁COUNT查询
        cacheManager.registerCustomCache(CACHE_UNREAD_COUNT,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(30))
                        .maximumSize(10000)
                        .recordStats()
                        .build());
        
        // 用户星球成员关系缓存: 5分钟过期,最大50000条
        // 优化: 频繁检查,减少数据库查询
        cacheManager.registerCustomCache(CACHE_PLANET_MEMBER,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .maximumSize(50000)
                        .recordStats()
                        .build());
    
        return cacheManager;
    }
}
