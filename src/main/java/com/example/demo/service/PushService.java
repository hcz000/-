package com.example.demo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.entity.Postings;
import com.example.demo.enums.PostingType;
import com.example.demo.repository.PostingsRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PushService {

    private static final String DAILY_POSTS_KEY = "push:posts:daily";
    private static final String WEEKLY_POSTS_KEY = "push:posts:weekly";
    private static final String USER_PUSH_KEY_PREFIX = "push:user:";
    private static final String REBUILD_LOCK_KEY_PREFIX = "push:lock:";

    private static final long CACHE_EXPIRE_HOURS = 1;
    private static final long USER_PUSH_CACHE_SECONDS = 30;
    private static final long REBUILD_LOCK_SECONDS = 10;

    private static final int TOTAL_PUSH_COUNT = 10;
    private static final int TYPE_CACHE_CANDIDATE_SIZE = 200;
    private static final int DEFAULT_CACHE_CANDIDATE_SIZE = 300;
    private static final int PUSH_TIMEOUT_MS = 250;

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UserVectorService userVectorService;

    public List<Postings> push() {
        List<Long> mergedIds = new ArrayList<>(TOTAL_PUSH_COUNT + 2);
        mergedIds.addAll(getRandomIdsFromCache(DAILY_POSTS_KEY, 4, 1));
        mergedIds.addAll(getRandomIdsFromCache(WEEKLY_POSTS_KEY, 6, 7));
        return materializeByOrderedIds(mergedIds, TOTAL_PUSH_COUNT, "push.random");
    }

    public List<Postings> pushForCurrentUser() {
        if (!StpUtil.isLogin()) {
            return push();
        }
        Long userId = StpUtil.getLoginIdAsLong();
        if (!userVectorService.isStableStage(userId)) {
            return push();
        }

        String userPushCacheKey = USER_PUSH_KEY_PREFIX + userId;
        List<Postings> cached = getUserPushFromCache(userPushCacheKey);
        if (!cached.isEmpty()) {
            log.debug("[push.a] cache hit userId={}, size={}", userId, cached.size());
            return cached;
        }

        long startNs = System.nanoTime();
        try {
            List<Postings> personalized = CompletableFuture
                    .supplyAsync(this::likepush)
                    .orTimeout(PUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("[push.a] personalized timeout/fail userId={}, fallback=random, reason={}",
                                userId, ex.toString());
                        return push();
                    })
                    .join();
            cacheUserPush(userPushCacheKey, personalized);
            log.info("[push.a] done userId={}, total={}ms, resultSize={}",
                    userId, elapsedMs(startNs), personalized.size());
            return personalized;
        } catch (Exception e) {
            log.warn("[push.a] unexpected error userId={}, fallback=random", userId, e);
            return push();
        }
    }

    public List<Postings> likepush() {
        long totalStart = System.nanoTime();

        if (!StpUtil.isLogin()) {
            return push();
        }
        Long userId = StpUtil.getLoginIdAsLong();

        long vectorStart = System.nanoTime();
        List<UserVectorService.TypeRatio> ratios = userVectorService.getTypeRatios(userId);
        long vectorCost = elapsedMs(vectorStart);

        log.info("user {} type ratios: {}", userId,
                ratios.stream().map(r -> r.typeName() + "=" + (int) (r.ratio() * 100) + "%").toList());

        List<Long> mergedIds = new ArrayList<>(TOTAL_PUSH_COUNT * 2);
        Set<Long> seenIds = new HashSet<>(TOTAL_PUSH_COUNT * 2);

        int interestCount = 0;
        List<Integer> typeCounts = new ArrayList<>();
        for (UserVectorService.TypeRatio ratio : ratios) {
            int count = (int) (ratio.ratio() * TOTAL_PUSH_COUNT);
            typeCounts.add(count);
            interestCount += count;
        }

        log.info("interest allocation={}, random补位={}", interestCount, TOTAL_PUSH_COUNT - interestCount);

        long typedStart = System.nanoTime();
        for (int i = 0; i < ratios.size(); i++) {
            UserVectorService.TypeRatio ratio = ratios.get(i);
            int count = typeCounts.get(i);
            if (count <= 0) {
                continue;
            }

            PostingType type = ratio.type();
            String typeCacheKey = "push:posts:type:" + type.getTypeName();

            List<Long> typeIds = getPostIdsByTypeFromCache(typeCacheKey, type, count);
            for (Long id : typeIds) {
                if (id == null) {
                    continue;
                }
                if (seenIds.add(id)) {
                    mergedIds.add(id);
                }
            }
        }
        long typedCost = elapsedMs(typedStart);

        int remaining = TOTAL_PUSH_COUNT - mergedIds.size();
        long randomStart = System.nanoTime();
        if (remaining > 0) {
            List<Long> randomIds = getRandomIdsFromCache(WEEKLY_POSTS_KEY, remaining * 2, 7);
            for (Long id : randomIds) {
                if (id == null) {
                    continue;
                }
                if (seenIds.add(id) && mergedIds.size() < TOTAL_PUSH_COUNT) {
                    mergedIds.add(id);
                }
            }
        }
        long randomCost = elapsedMs(randomStart);

        List<Postings> result = materializeByOrderedIds(mergedIds, TOTAL_PUSH_COUNT, "push.a");
        log.info("[push.a] perf userId={}, vector={}ms, typed={}ms, randomFill={}ms, total={}ms, finalSize={}",
                userId, vectorCost, typedCost, randomCost, elapsedMs(totalStart), result.size());

        return result;
    }

    private List<Long> getPostIdsByTypeFromCache(String cacheKey, PostingType type, int count) {
        Long cacheSize = redisTemplate.opsForSet().size(cacheKey);

        if (cacheSize == null || cacheSize < count * 2L) {
            fillTypeCache(cacheKey, type);
        }

        Set<Object> ids = redisTemplate.opsForSet().distinctRandomMembers(cacheKey, count);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return parseIds(ids);
    }

    private void fillTypeCache(String cacheKey, PostingType type) {
        if (!acquireRebuildLock(cacheKey)) {
            return;
        }

        long startNs = System.nanoTime();
        LocalDateTime startTime = LocalDateTime.now().minusDays(7);

        Specification<Postings> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            predicate = cb.and(predicate, cb.equal(root.get("status"), 1));
            predicate = cb.and(predicate, cb.equal(root.get("auditStatus"), 1));
            predicate = cb.and(predicate, cb.equal(root.get("type"), type.getTypeName()));
            return predicate;
        };

        List<Postings> posts = postingsRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createTime"));
        if (posts.isEmpty()) {
            return;
        }

        if (posts.size() > TYPE_CACHE_CANDIDATE_SIZE) {
            posts = posts.subList(0, TYPE_CACHE_CANDIDATE_SIZE);
        }

        Long[] ids = posts.stream()
                .map(Postings::getPostingsId)
                .toArray(Long[]::new);

        redisTemplate.delete(cacheKey);
        redisTemplate.opsForSet().add(cacheKey, (Object[]) ids);
        redisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        log.info("fill type cache {}: {} ids (type={}, cost={}ms)",
                cacheKey, ids.length, type.getTypeName(), elapsedMs(startNs));
    }

    private List<Long> getRandomIdsFromCache(String key, int count, int days) {
        Long cacheSize = redisTemplate.opsForSet().size(key);

        if (cacheSize == null || cacheSize < count * 3L) {
            log.info("cache insufficient, lazy refill: {}", key);
            fillCache(key, days);
        }

        Set<Object> ids = redisTemplate.opsForSet().distinctRandomMembers(key, count);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return parseIds(ids);
    }

    private void fillCache(String key, int days) {
        if (!acquireRebuildLock(key)) {
            return;
        }

        long startNs = System.nanoTime();
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        Specification<Postings> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            predicate = cb.and(predicate, cb.equal(root.get("status"), 1));
            predicate = cb.and(predicate, cb.equal(root.get("auditStatus"), 1));
            return predicate;
        };

        List<Postings> posts = postingsRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createTime"));
        if (posts.isEmpty()) {
            return;
        }

        if (posts.size() > DEFAULT_CACHE_CANDIDATE_SIZE) {
            posts = posts.subList(0, DEFAULT_CACHE_CANDIDATE_SIZE);
        }

        Long[] ids = posts.stream()
                .map(Postings::getPostingsId)
                .toArray(Long[]::new);

        redisTemplate.delete(key);
        redisTemplate.opsForSet().add(key, (Object[]) ids);
        redisTemplate.expire(key, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        log.info("fill cache {}: {} ids, cost={}ms", key, ids.length, elapsedMs(startNs));
    }

    private boolean acquireRebuildLock(String cacheKey) {
        String lockKey = REBUILD_LOCK_KEY_PREFIX + cacheKey;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", REBUILD_LOCK_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }

    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }

    private List<Postings> loadPostsByIdsTimed(List<Long> idList, String sourceKey) {
        long startNs = System.nanoTime();
        List<Postings> posts = postingsRepository.findAllById(idList);
        log.debug("[push.a] loadByIds source={}, size={}, cost={}ms", sourceKey, idList.size(), elapsedMs(startNs));
        return posts;
    }

    private List<Postings> materializeByOrderedIds(List<Long> orderedIds, int maxCount, String sourceKey) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> dedupOrdered = new ArrayList<>(Math.min(orderedIds.size(), maxCount));
        Set<Long> seen = new HashSet<>(orderedIds.size());
        for (Long id : orderedIds) {
            if (id == null || !seen.add(id)) {
                continue;
            }
            dedupOrdered.add(id);
            if (dedupOrdered.size() >= maxCount) {
                break;
            }
        }
        if (dedupOrdered.isEmpty()) {
            return Collections.emptyList();
        }
        List<Postings> rows = loadPostsByIdsTimed(dedupOrdered, sourceKey);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Postings> result = new ArrayList<>(rows.size());
        java.util.Map<Long, Postings> postMap = new java.util.HashMap<>(rows.size());
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

    private List<Long> parseIds(Set<Object> ids) {
        List<Long> parsed = new ArrayList<>(ids.size());
        for (Object id : ids) {
            if (id == null) {
                continue;
            }
            try {
                parsed.add(Long.parseLong(id.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return parsed;
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
