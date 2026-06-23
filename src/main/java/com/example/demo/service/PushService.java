package com.example.demo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.entity.FriendRelation;
import com.example.demo.entity.Postings;
import com.example.demo.repository.FriendRelationRepository;
import com.example.demo.repository.PlanetMemberRepository;
import com.example.demo.repository.PostingsRepository;
import com.example.demo.service.recommend.RecommendStrategy;
import com.example.demo.service.recommend.RecommendStrategy.StrategyResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private PlanetMemberRepository planetMemberRepository;

    @Resource
    private FriendRelationRepository friendRelationRepository;

    @Resource
    private HotRankService hotRankService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String DETAIL_KEY_PREFIX = "push:detail:";
    private static final long DETAIL_TTL_DAYS = 7;
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

            // 并行启动两个策略（只返回 ID，不查数据库）：兴趣优先，随机兜底补全
            CompletableFuture<List<Long>> interestIdsFuture = CompletableFuture
                    .supplyAsync(() -> {
                        StrategyResult result = active.recommend(currentUserId, TOTAL_PUSH_COUNT);
                        log.debug("[push.a] interest strategy done, ids={}", result.postIds().size());
                        return result.postIds();
                    }, virtualThreadExecutor);

            CompletableFuture<List<Long>> randomIdsFuture = CompletableFuture
                    .supplyAsync(() -> {
                        StrategyResult result = randomStrategy.recommend(currentUserId, TOTAL_PUSH_COUNT);
                        log.debug("[push.a] random strategy done, ids={}", result.postIds().size());
                        return result.postIds();
                    }, virtualThreadExecutor);

            // 等待兴趣策略结果（带超时）
            List<Long> interestIds;
            try {
                interestIds = interestIdsFuture.orTimeout(PUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS).join();
            } catch (Exception e) {
                interestIds = Collections.emptyList();
                log.warn("[push.a] interest strategy timeout/fail userId={}, strategy={}, reason={}",
                        userId, active.getName(), e.toString());
            }

            // 合并 ID：兴趣优先，随机补全
            List<Long> hotIds = hotRankService.topIds(TOTAL_PUSH_COUNT);
            List<Long> mergedIds = mergeIds(interestIds, hotIds, randomIdsFuture, TOTAL_PUSH_COUNT);

            // 统一查一次数据库，获取帖子详情
            List<Postings> result = materializeByOrderedIds(mergedIds, TOTAL_PUSH_COUNT,
                    "push.a:" + active.getName());

            cacheUserPush(userPushCacheKey, result);
            log.info("[push.a] done userId={}, strategy={}, cost={}ms, interestSize={}, finalSize={}",
                    userId, active.getName(), elapsedMs(startNs), interestIds.size(), result.size());
            return result;
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
        return interestPushForUser(StpUtil.getLoginIdAsLong());
    }

    /**
     * 按指定用户的兴趣模型生成推荐，只供当前服务内部复用。
     */
    private List<Postings> interestPushForUser(Long userId) {
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
    public List<Postings> hotPush(int count) {
        int limit = count <= 0 ? TOTAL_PUSH_COUNT : count;
        List<Long> ids = hotRankService.topIds(limit);
        if (ids.isEmpty()) {
            hotRankService.rebuildRecent(500);
            ids = hotRankService.topIds(limit);
        }
        return materializeByOrderedIds(ids, limit, "push.hot");
    }

    public List<Postings> planetPush(Long userId, int count) {
        if (userId == null) {
            return push();
        }
        int limit = count <= 0 ? TOTAL_PUSH_COUNT : count;
        List<Long> planetIds = planetMemberRepository.findPlanetIdsByUserId(userId);
        if (planetIds == null || planetIds.isEmpty()) {
            return push();
        }
        List<Long> ids = postingsRepository.findRecentLiveIdsByPlanetIds(planetIds, limit);
        return materializeByOrderedIds(ids, limit, "push.planet");
    }

    public List<Postings> friendPush(Long userId, int count) {
        if (userId == null) {
            return push();
        }
        int limit = count <= 0 ? TOTAL_PUSH_COUNT : count;
        List<Long> friendIds = friendRelationRepository.findByUserIdInRelation(userId).stream()
                .map(relation -> relation.getUserAId().equals(userId) ? relation.getUserBId() : relation.getUserAId())
                .filter(Objects::nonNull)
                .toList();
        if (friendIds.isEmpty()) {
            return push();
        }
        List<Long> ids = postingsRepository.findRecentLiveIdsByUserIds(friendIds, limit);
        return materializeByOrderedIds(ids, limit, "push.friend");
    }

    private RecommendStrategy getActiveStrategy() {
        RecommendStrategy strategy = strategies.get(activeStrategyName);
        if (strategy == null) {
            log.warn("Unknown strategy '{}', fallback to interestStrategy", activeStrategyName);
            return interestStrategy;
        }
        log.debug("[push.a] active strategy: {}", strategy.getName());
        return strategy;
    }

    // ===== 结果合并 =====

    /**
     * 合并 ID 列表：兴趣优先，随机补全
     * <p>
     * 如果兴趣 ID 不足 maxCount，从随机结果中取不重复的 ID 补齐
     */
    private List<Long> mergeIds(List<Long> interestIds,
                                List<Long> hotIds,
                                CompletableFuture<List<Long>> randomIdsFuture,
                                int maxCount) {
        // 兴趣结果够用 → 直接去重返回
        if (interestIds.size() >= maxCount) {
            return dedupLimit(interestIds, maxCount);
        }

        // 等随机结果（此时大概率已经完成了）
        List<Long> randomIds;
        try {
            randomIds = randomIdsFuture.join();
        } catch (Exception e) {
            log.warn("[push.a] random strategy also failed", e);
            return dedupLimit(interestIds, maxCount);
        }

        // 去重合并：兴趣优先，随机补全
        Set<Long> seenIds = new HashSet<>();
        List<Long> merged = new ArrayList<>(maxCount);
        for (Long id : interestIds) {
            if (id != null && seenIds.add(id)) {
                merged.add(id);
            }
        }
        if (hotIds != null) {
            for (Long id : hotIds) {
                if (merged.size() >= maxCount) break;
                if (id != null && seenIds.add(id)) {
                    merged.add(id);
                }
            }
        }
        for (Long id : randomIds) {
            if (merged.size() >= maxCount) break;
            if (id != null && seenIds.add(id)) {
                merged.add(id);
            }
        }
        return merged;
    }

    private List<Long> dedupLimit(List<Long> ids, int maxCount) {
        Set<Long> seen = new HashSet<>();
        List<Long> result = new ArrayList<>(maxCount);
        for (Long id : ids) {
            if (id != null && seen.add(id)) {
                result.add(id);
                if (result.size() >= maxCount) break;
            }
        }
        return result;
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
        // 去重 + 截断
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

        // 1. 先从 Redis 批量读取帖子详情
        Map<Long, Postings> postMap = loadFromRedisDetail(dedupOrdered);

        // 2. 找出 Redis 未命中的 ID
        List<Long> missedIds = new ArrayList<>();
        for (Long id : dedupOrdered) {
            if (!postMap.containsKey(id)) {
                missedIds.add(id);
            }
        }

        // 3. 未命中的批量查数据库并回填 Redis
        if (!missedIds.isEmpty()) {
            long startNs = System.nanoTime();
            List<Postings> pgRows = postingsRepository.findAllById(missedIds);
            log.debug("[push] db fallback ids={}, hits={}, cost={}ms",
                    missedIds.size(), pgRows.size(), elapsedMs(startNs));
            for (Postings row : pgRows) {
                if (row != null && row.getPostingsId() != null) {
                    postMap.put(row.getPostingsId(), row);
                    cachePostDetail(row);  // 回填 Redis
                }
            }
        }

        // 4. 按原始顺序组装结果
        List<Postings> result = new ArrayList<>(postMap.size());
        for (Long id : dedupOrdered) {
            Postings post = postMap.get(id);
            if (post != null) {
                result.add(post);
            }
        }
        return result;
    }

    // ===== Redis 帖子详情缓存 =====

    /**
     * 从 Redis 批量读取帖子详情（Pipeline）
     */
    private Map<Long, Postings> loadFromRedisDetail(List<Long> ids) {
        Map<Long, Postings> result = new HashMap<>(ids.size());
        try {
            List<Object> responses = stringRedisTemplate.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (Long id : ids) {
                            byte[] key = stringRedisTemplate.getStringSerializer()
                                    .serialize(DETAIL_KEY_PREFIX + id);
                            if (key != null) {
                                connection.stringCommands().get(key);
                            }
                        }
                        return null;
                    });

            for (int i = 0; i < ids.size() && i < responses.size(); i++) {
                Object raw = responses.get(i);
                if (raw instanceof String json) {
                    try {
                        Postings post = MAPPER.readValue(json, Postings.class);
                        if (post != null && post.getPostingsId() != null) {
                            result.put(post.getPostingsId(), post);
                        }
                    } catch (JsonProcessingException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[push] redis detail pipeline failed", e);
        }
        if (!result.isEmpty()) {
            log.debug("[push] redis detail hit {}/{}", result.size(), ids.size());
        }
        return result;
    }

    /**
     * 将帖子详情写入 Redis（7天过期）
     */
    private void cachePostDetail(Postings post) {
        if (post == null || post.getPostingsId() == null) return;
        try {
            String json = MAPPER.writeValueAsString(post);
            stringRedisTemplate.opsForValue().set(
                    DETAIL_KEY_PREFIX + post.getPostingsId(),
                    json,
                    DETAIL_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            log.warn("[push] cache post detail failed, postId={}", post.getPostingsId(), e);
        }
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
