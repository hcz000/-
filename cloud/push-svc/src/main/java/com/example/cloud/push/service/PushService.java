package com.example.cloud.push.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.cloud.push.entity.Postings;
import com.example.cloud.push.repository.FriendRelationRepository;
import com.example.cloud.push.repository.PlanetMemberRepository;
import com.example.cloud.push.repository.PostingsRepository;
import com.example.cloud.push.service.recommend.RecommendStrategy;
import com.example.cloud.push.service.recommend.RecommendStrategy.StrategyResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 推荐协调服务
 * 负责认证校验、超时降级等编排逻辑，核心算法委托给 {@link RecommendStrategy}
 * <p>
 * 「已曝光去重」由 {@link SeenPostService} + 调用方传入的 {@code excludeIds} 共同完成，
 * Controller 负责拼接最终排除集，并在结果返回后调用 {@code markSeen} 记录。
 */
@Slf4j
@Service
public class PushService {

    private static final int TOTAL_PUSH_COUNT = 10;
    private static final int PUSH_TIMEOUT_MS = 250;
    /** 确定性查询（hot/planet/friend）的内存过滤超采系数 */
    private static final int OVERSAMPLE_FACTOR = 3;

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

    @Value("${app.recommend.strategy:interestStrategy}")
    private String activeStrategyName;

    // ===== 对外 API =====

    public List<Postings> push(Set<Long> excludeIds) {
        Set<Long> exclude = nonNull(excludeIds);
        StrategyResult result = randomStrategy.recommend(null, TOTAL_PUSH_COUNT, exclude);
        return materializeByOrderedIds(result.postIds(), TOTAL_PUSH_COUNT, "push.random");
    }

    public List<Postings> pushForCurrentUser(Set<Long> excludeIds) {
        if (!StpUtil.isLogin()) {
            return push(excludeIds);
        }
        Long userId = StpUtil.getLoginIdAsLong();
        if (!userVectorService.isStableStage(userId)) {
            return push(excludeIds);
        }

        Set<Long> exclude = nonNull(excludeIds);
        long startNs = System.nanoTime();
        try {
            Long currentUserId = userId;
            RecommendStrategy active = getActiveStrategy();

            CompletableFuture<List<Long>> interestIdsFuture = CompletableFuture
                    .supplyAsync(() -> {
                        StrategyResult result = active.recommend(currentUserId, TOTAL_PUSH_COUNT, exclude);
                        log.debug("[push.a] interest strategy done, ids={}", result.postIds().size());
                        return result.postIds();
                    }, virtualThreadExecutor);

            CompletableFuture<List<Long>> randomIdsFuture = CompletableFuture
                    .supplyAsync(() -> {
                        StrategyResult result = randomStrategy.recommend(currentUserId, TOTAL_PUSH_COUNT, exclude);
                        log.debug("[push.a] random strategy done, ids={}", result.postIds().size());
                        return result.postIds();
                    }, virtualThreadExecutor);

            List<Long> interestIds;
            try {
                interestIds = interestIdsFuture.orTimeout(PUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS).join();
            } catch (Exception e) {
                interestIds = Collections.emptyList();
                log.warn("[push.a] interest strategy timeout/fail userId={}, strategy={}, reason={}",
                        userId, active.getName(), e.toString());
            }

            List<Long> hotIds = hotRankService.topIds(TOTAL_PUSH_COUNT * OVERSAMPLE_FACTOR);
            List<Long> mergedIds = mergeIds(interestIds, hotIds, randomIdsFuture, TOTAL_PUSH_COUNT, exclude);

            List<Postings> result = materializeByOrderedIds(mergedIds, TOTAL_PUSH_COUNT,
                    "push.a:" + active.getName());

            log.info("[push.a] done userId={}, strategy={}, cost={}ms, interestSize={}, finalSize={}, exclude={}",
                    userId, active.getName(), elapsedMs(startNs), interestIds.size(), result.size(), exclude.size());
            return result;
        } catch (Exception e) {
            log.warn("[push.a] unexpected error userId={}, fallback=random", userId, e);
            return push(exclude);
        }
    }

    public List<Postings> likepush(Set<Long> excludeIds) {
        if (!StpUtil.isLogin()) {
            return push(excludeIds);
        }
        return interestPushForUser(StpUtil.getLoginIdAsLong(), nonNull(excludeIds));
    }

    private List<Postings> interestPushForUser(Long userId, Set<Long> exclude) {
        if (userId == null) {
            return push(exclude);
        }
        long startNs = System.nanoTime();
        StrategyResult result = interestStrategy.recommend(userId, TOTAL_PUSH_COUNT, exclude);
        List<Postings> posts = materializeByOrderedIds(result.postIds(), TOTAL_PUSH_COUNT, "push.a:interest");
        log.info("[push.a:interest] userId={}, strategy={}, cost={}ms, finalSize={}, exclude={}",
                userId, result.strategyName(), elapsedMs(startNs), posts.size(), exclude.size());
        return posts;
    }

    public List<Postings> hotPush(int count, Set<Long> excludeIds) {
        int limit = count <= 0 ? TOTAL_PUSH_COUNT : count;
        Set<Long> exclude = nonNull(excludeIds);
        List<Long> ids = hotRankService.topIds(limit * OVERSAMPLE_FACTOR);
        if (ids.isEmpty()) {
            hotRankService.rebuildRecent(500);
            ids = hotRankService.topIds(limit * OVERSAMPLE_FACTOR);
        }
        List<Long> filtered = filterAndTake(ids, exclude, limit);
        return materializeByOrderedIds(filtered, limit, "push.hot");
    }

    public List<Postings> planetPush(Long userId, int count, Set<Long> excludeIds) {
        if (userId == null) {
            return push(excludeIds);
        }
        int limit = count <= 0 ? TOTAL_PUSH_COUNT : count;
        Set<Long> exclude = nonNull(excludeIds);
        List<Long> planetIds = planetMemberRepository.findPlanetIdsByUserId(userId);
        if (planetIds == null || planetIds.isEmpty()) {
            return push(exclude);
        }
        List<Long> ids = postingsRepository.findRecentLiveIdsByPlanetIds(planetIds, limit * OVERSAMPLE_FACTOR);
        List<Long> filtered = filterAndTake(ids, exclude, limit);
        return materializeByOrderedIds(filtered, limit, "push.planet");
    }

    public List<Postings> friendPush(Long userId, int count, Set<Long> excludeIds) {
        if (userId == null) {
            return push(excludeIds);
        }
        int limit = count <= 0 ? TOTAL_PUSH_COUNT : count;
        Set<Long> exclude = nonNull(excludeIds);
        List<Long> friendIds = friendRelationRepository.findByUserIdInRelation(userId).stream()
                .map(relation -> relation.getUserAId().equals(userId) ? relation.getUserBId() : relation.getUserAId())
                .filter(Objects::nonNull)
                .toList();
        if (friendIds.isEmpty()) {
            return push(exclude);
        }
        List<Long> ids = postingsRepository.findRecentLiveIdsByUserIds(friendIds, limit * OVERSAMPLE_FACTOR);
        List<Long> filtered = filterAndTake(ids, exclude, limit);
        return materializeByOrderedIds(filtered, limit, "push.friend");
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

    private List<Long> mergeIds(List<Long> interestIds,
                                List<Long> hotIds,
                                CompletableFuture<List<Long>> randomIdsFuture,
                                int maxCount,
                                Set<Long> excludeIds) {
        Set<Long> seenIds = new HashSet<>();
        List<Long> merged = new ArrayList<>(maxCount);

        appendUnique(interestIds, excludeIds, seenIds, merged, maxCount);
        if (merged.size() >= maxCount) return merged;

        appendUnique(hotIds, excludeIds, seenIds, merged, maxCount);
        if (merged.size() >= maxCount) return merged;

        List<Long> randomIds;
        try {
            randomIds = randomIdsFuture.join();
        } catch (Exception e) {
            log.warn("[push.a] random strategy also failed", e);
            return merged;
        }
        appendUnique(randomIds, excludeIds, seenIds, merged, maxCount);
        return merged;
    }

    private void appendUnique(List<Long> source, Set<Long> excludeIds,
                              Set<Long> seenIds, List<Long> merged, int maxCount) {
        if (source == null) return;
        for (Long id : source) {
            if (merged.size() >= maxCount) return;
            if (id == null || excludeIds.contains(id)) continue;
            if (seenIds.add(id)) {
                merged.add(id);
            }
        }
    }

    private List<Long> filterAndTake(List<Long> ids, Set<Long> excludeIds, int maxCount) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<Long> result = new ArrayList<>(Math.min(ids.size(), maxCount));
        Set<Long> seen = new HashSet<>(ids.size());
        for (Long id : ids) {
            if (id == null || excludeIds.contains(id)) continue;
            if (seen.add(id)) {
                result.add(id);
                if (result.size() >= maxCount) break;
            }
        }
        return result;
    }

    private Set<Long> nonNull(Set<Long> in) {
        return in == null ? Collections.emptySet() : in;
    }

    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
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

        Map<Long, Postings> postMap = loadFromRedisDetail(dedupOrdered);

        List<Long> missedIds = new ArrayList<>();
        for (Long id : dedupOrdered) {
            if (!postMap.containsKey(id)) {
                missedIds.add(id);
            }
        }

        if (!missedIds.isEmpty()) {
            long startNs = System.nanoTime();
            List<Postings> pgRows = postingsRepository.findAllById(missedIds);
            log.debug("[push] db fallback ids={}, hits={}, cost={}ms",
                    missedIds.size(), pgRows.size(), elapsedMs(startNs));
            for (Postings row : pgRows) {
                if (row != null && row.getPostingsId() != null) {
                    postMap.put(row.getPostingsId(), row);
                    cachePostDetail(row);
                }
            }
        }

        List<Postings> result = new ArrayList<>(postMap.size());
        for (Long id : dedupOrdered) {
            Postings post = postMap.get(id);
            if (post != null) {
                result.add(post);
            }
        }
        return result;
    }

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
}
