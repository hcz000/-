package com.example.cloud.common.constant;

/**
 * 应用层缓存名称常量集合。
 * <p>
 * 当前简化版 service 都去掉了 @Cacheable 直接查 DB；
 * 这些常量保留供生产场景重新启用 Spring Cache + Caffeine/Redis 时使用。
 *
 * 升级路径示例：
 * <pre>
 *   @Cacheable(value = CacheNames.POSTINGS, key = "#id")
 *   public Postings getPost(Long id) { ... }
 * </pre>
 */
public final class CacheNames {

    private CacheNames() {}

    public static final String POSTINGS = "postings";
    public static final String LIKE_COUNT = "likeCount";
    public static final String USER_LIKED = "userLiked";
    public static final String USER_BY_EMAIL = "userByEmail";
    public static final String USER_BY_ID = "userById";
    public static final String MY_REPORTS = "myReports";
    public static final String USER_INTEREST_RATIOS = "userInterestRatios";
    public static final String SYSTEM_CONFIG = "systemConfig";
    public static final String HOT_POSTS = "hotPosts";
    public static final String PLANET = "planet";
    public static final String UNREAD_COUNT = "unreadCount";
}
