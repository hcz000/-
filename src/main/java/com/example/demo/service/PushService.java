package com.example.demo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.entity.Postings;
import com.example.demo.repository.PostingsRepository;
import com.example.demo.service.recommend.RecommendStrategy;
import com.example.demo.service.recommend.RecommendStrategy.StrategyResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 推荐协调服务
 * 负责认证校验、缓存、超时降级等编排逻辑，核心算法委托给 {@link RecommendStrategy}
 */
@Slf4j
@Service
public class PushService {

    private static final String USER_PUSH_KEY_PREFIX = "push:user:";
    private static final long USER_PUSH_CACHE_SECONDS = 30;
    private static final int TOTAL_PUSH_COUNT = 10;
    private static final int PUSH_TIMEOUT_MS = 250;

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UserVectorService userVectorService;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private Executor virtualThreadExecutor;

    @Resource
    private Map<String, RecommendStrategy> strategies;

    @Resource
    @Qualifier("interestStrategy")
    private RecommendStrategy interestStrategy;

    @Resource
    @Qualifier("randomStrategy")
    private RecommendStrategy randomStrategy;

    /**
     * 当前激活的推荐策略名称，通过 application.yml 配置
     */
    @Value("${app.recommend.strategy:interestStrategy}")
    private String activeStrategyName;

    // ===== 对外 API =====

    /**
     * 随机推送（兜底策略）
     */
    public List<Postings> push() {
        StrategyResult result = randomStrategy.recommend(null, TOTAL_PUSH_COUNT);
        return materializeByOrderedIds(result.postIds(), TOTAL_PUSH_COUNT, "push.random");
    }

    /**
     * 首页智能推送：根据登录状态和用户向量稳定性选择策略
     */
    public List<Postings> pushForCurrentUser() {
        if (!StpUtil.isLogin()) {
            return push();
        }
        Long userId = StpUtil.getLoginIdAsLong();
        if (!userVectorService.isStableStage(userId)) {
            return push();
        }

        // 检查用户缓存
        String userPushCacheKey = USER_PUSH_KEY_PREFIX + userId;
        List<Postings> cached = getUserPushFromCache(userPushCacheKey);
        if (!cached.isEmpty()) {
            log.debug("[push.a] cache hit userId={}, size={}", userId, cached.size());
            return cached;
        }

        long startNs = System.nanoTime();
        try {
            Long currentUserId = userId;
            RecommendStrategy active = getActiveStrategy();
            List<Postings> personalized = CompletableFuture
                    .supplyAsync(() -> {
                        StrategyResult result = active.recommend(currentUserId, TOTAL_PUSH_COUNT);
                        return materializeByOrderedIds(result.postIds(), TOTAL_PUSH_COUNT,
                                "push.a:" + result.strategyName());
                    }, virtualThreadExecutor)
                    .orTimeout(PUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("[push.a] strategy timeout/fail userId={}, strategy={}, fallback=random, reason={}",
                                userId, active.getName(), ex.toString());
                        StrategyResult fallback = randomStrategy.recommend(currentUserId, TOTAL_PUSH_COUNT);
                        return materializeByOrderedIds(fallback.postIds(), TOTAL_PUSH_COUNT, "push.a:fallback");
                    })
                    .join();
            cacheUserPush(userPushCacheKey, personalized);
            log.info("[push.a] done userId={}, strategy={}, cost={}ms, resultSize={}",
                    userId, active.getName(), elapsedMs(startNs), personalized.size());
            return personalized;
        } catch (Exception e) {
            log.warn("[push.a] unexpected error userId={}, fallback=random", userId, e);
            return push();
        }
    }

    /**
     * 按兴趣推送（内部使用，供 Controller 暴露）
     */
    public List<Postings> likepush() {
        if (!StpUtil.isLogin()) {
            return push();
        }
        return likepush(StpUtil.getLoginIdAsLong());
    }

    /**
     * 按用户 ID 兴趣推送（供 RabbitMQ 监听器等内部调用方使用）
     */
    public List<Postings> likepush(Long userId) {
        if (userId == null) {
            return push();
        }
        long startNs = System.nanoTime();
        StrategyResult result = interestStrategy.recommend(userId, TOTAL_PUSH_COUNT);
        List<Postings> posts = materializeByOrderedIds(result.postIds(), TOTAL_PUSH_COUNT, "push.a:interest");
        log.info("[push.a:interest] userId={}, strategy={}, cost={}ms, finalSize={}",
                userId, result.strategyName(), elapsedMs(startNs), posts.size());
        return posts;
    }

    // ===== 策略选择 =====

    /**
     * 根据配置获取当前激活的推荐策略
     */
    private RecommendStrategy getActiveStrategy() {
        RecommendStrategy strategy = strategies.get(activeStrategyName);
        if (strategy == null) {
            log.warn("Unknown strategy '{}', fallback to interestStrategy", activeStrategyName);
            return interestStrategy;
        }
        log.debug("[push.a] active strategy: {}", strategy.getName());
        return strategy;
    }

    // ===== 通用工具方法 =====

    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }

    private List<Postings> loadPostsByIdsTimed(List<Long> idList, String sourceKey) {
        long startNs = System.nanoTime();
        List<Postings> posts = postingsRepository.findAllById(idList);
        log.debug("[push] loadByIds source={}, size={}, cost={}ms", sourceKey, idList.size(), elapsedMs(startNs));
        return posts;
    }

    private List<Postings> materializeByOrderedIds(List<Long> orderedIds, int maxCount, String sourceKey) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> dedupOrdered = new ArrayList<>(Math.min(orderedIds.size(), maxCount));
        Set<Long> seen = new HashSet<>(orderedIds.size());
        for (Long id : orderedIds) {
            if (id == null || !seen.add(id)) continue;
            dedupOrdered.add(id);
            if (dedupOrdered.size() >= maxCount) break;
        }
        if (dedupOrdered.isEmpty()) {
            return Collections.emptyList();
        }
        List<Postings> rows = loadPostsByIdsTimed(dedupOrdered, sourceKey);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Postings> result = new ArrayList<>(rows.size());
        Map<Long, Postings> postMap = new HashMap<>(rows.size());
        for (Postings row : rows) {
            if (row != null && row.getPostingsId() != null) {
                postMap.put(row.getPostingsId(), row);
            }
        }
        for (Long id : dedupOrdered) {
            Postings post = postMap.get(id);
            if (post != null) {
                result.add(post);
            }
        }
        return result;
    }

    private List<Postings> getUserPushFromCache(String userPushCacheKey) {
        Object obj = redisTemplate.opsForValue().get(userPushCacheKey);
        if (!(obj instanceof String cachedCsv) || cachedCsv.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> ids = Arrays.stream(cachedCsv.split(","))
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return loadPostsByIdsTimed(ids, userPushCacheKey);
    }

    private void cacheUserPush(String userPushCacheKey, List<Postings> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        String ids = posts.stream()
                .map(Postings::getPostingsId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (ids.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(userPushCacheKey, ids, USER_PUSH_CACHE_SECONDS, TimeUnit.SECONDS);
    }
}
