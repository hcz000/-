package com.example.demo.service;

import com.example.demo.config.CacheConfig;
import com.example.demo.entity.UserInterestModel;
import com.example.demo.enums.PostingType;
import com.example.demo.repository.UserInterestModelRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserVectorService {

    private static final String USER_MODEL_KEY_PREFIX = "user:model:";
    private static final String TYPE_RATIOS_CACHE_KEY_PREFIX = "user:vector:ratios:";
    private static final String EVENT_COUNT_KEY_PREFIX = "user:vector:count:";
    /** 向量缓存 TTL，与 Sa-Token timeout 对齐（15分钟） */
    private static final long VECTOR_TTL_SECONDS = 900;

    @Value("${app.vector.alpha:0.1}")
    private float alpha;

    @Value("${app.vector.stable-threshold:5}")
    private int stableThreshold;

    @Resource
    private UserInterestModelRepository userInterestModelRepository;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheManager cacheManager;

    @Transactional(rollbackFor = Exception.class)
    public void updateUserVector(Long userId, PostingType postingType) {
        if (postingType == null) {
            log.warn("Posting type is null, skip vector update");
            return;
        }
        applyUserVectorBatch(userId, Collections.singletonList(postingType));
    }

    @Transactional(rollbackFor = Exception.class)
    public int applyUserVectorBatch(Long userId, List<PostingType> postingTypes) {
        if (userId == null || postingTypes == null || postingTypes.isEmpty()) {
            return 0;
        }

        int applied = 0;
        int[] typeCounts = new int[PostingType.VECTOR_DIMENSION];
        for (PostingType postingType : postingTypes) {
            if (postingType == null) {
                continue;
            }
            typeCounts[postingType.getIndex()]++;
            applied++;
        }
        if (applied <= 0) {
            return 0;
        }

        Map<String, Float> currentWeights = loadWeightsForUpdate(userId);
        Map<String, Float> targetWeights = new LinkedHashMap<>();
        PostingType[] types = PostingType.values();
        for (PostingType type : types) {
            targetWeights.put(type.getTypeName(), (float) typeCounts[type.getIndex()] / applied);
        }

        Map<String, Float> mergedWeights = new LinkedHashMap<>();
        float oneMinusAlpha = 1f - alpha;
        for (PostingType type : types) {
            String key = type.getTypeName();
            float oldValue = currentWeights.getOrDefault(key, defaultRatio());
            float targetValue = targetWeights.getOrDefault(key, 0f);
            mergedWeights.put(key, oldValue * oneMinusAlpha + targetValue * alpha);
        }
        normalize(mergedWeights);

        saveWeights(userId, mergedWeights, applied);
        cacheWeightsInRedis(userId, mergedWeights);
        stringRedisTemplate.opsForValue().increment(EVENT_COUNT_KEY_PREFIX + userId, applied);
        evictTypeRatiosCache(userId);

        log.info("Updated user interest model: userId={}, appliedEvents={}, alpha={}", userId, applied, alpha);
        return applied;
    }

    public void updateUserVector(Long userId, String postingTypeStr) {
        PostingType postingType = PostingType.fromString(postingTypeStr);
        if (postingType == null) {
            log.warn("Unknown posting type: {}", postingTypeStr);
            return;
        }
        updateUserVector(userId, postingType);
    }

    public List<TypeRatio> getTypeRatios(Long userId) {
        if (userId == null) {
            return getDefaultRatios();
        }

        List<TypeRatio> local = getTypeRatiosFromLocalCache(userId);
        if (local != null) {
            return local;
        }

        List<TypeRatio> redisRatios = getTypeRatiosFromRedis(userId);
        if (redisRatios != null) {
            cacheTypeRatiosLocally(userId, redisRatios);
            return redisRatios;
        }

        List<TypeRatio> dbRatios = loadTypeRatiosFromDb(userId);
        if (dbRatios != null) {
            cacheTypeRatiosInRedis(userId, dbRatios);
            cacheTypeRatiosLocally(userId, dbRatios);
            return dbRatios;
        }

        List<TypeRatio> defaults = getDefaultRatios();
        cacheTypeRatiosLocally(userId, defaults);
        return defaults;
    }

    public void evictTypeRatiosCache(Long userId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.delete(typeRatiosCacheKey(userId));
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_USER_INTEREST_RATIOS);
        if (cache != null) {
            cache.evict(userId);
        }
    }

    public boolean isStableStage(Long userId) {
        if (userId == null) {
            return false;
        }
        String countStr = stringRedisTemplate.opsForValue().get(EVENT_COUNT_KEY_PREFIX + userId);
        if (countStr == null) {
            int dbEvents = userInterestModelRepository.findByUserId(userId).stream()
                    .map(UserInterestModel::getEventCount)
                    .filter(v -> v != null)
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
            return dbEvents >= stableThreshold;
        }
        try {
            return Long.parseLong(countStr) >= stableThreshold;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Map<String, Float> loadWeightsForUpdate(Long userId) {
        Map<String, Float> weights = readWeightsFromRedis(userId);
        if (!weights.isEmpty()) {
            normalize(weights);
            return weights;
        }

        List<UserInterestModel> models = userInterestModelRepository.findByUserId(userId);
        for (UserInterestModel model : models) {
            if (model.getInterestKey() != null && model.getWeight() != null) {
                weights.put(model.getInterestKey(), model.getWeight());
            }
        }
        if (weights.isEmpty()) {
            weights = defaultWeights();
        }
        normalize(weights);
        cacheWeightsInRedis(userId, weights);
        return weights;
    }

    private List<TypeRatio> loadTypeRatiosFromDb(Long userId) {
        Map<String, Float> weights = new LinkedHashMap<>();
        List<UserInterestModel> models = userInterestModelRepository.findByUserId(userId);
        for (UserInterestModel model : models) {
            if (model.getInterestKey() != null && model.getWeight() != null) {
                weights.put(model.getInterestKey(), model.getWeight());
            }
        }
        if (weights.isEmpty()) {
            return null;
        }
        normalize(weights);
        cacheWeightsInRedis(userId, weights);
        return weightsToRatios(weights);
    }

    private void saveWeights(Long userId, Map<String, Float> weights, int appliedEvents) {
        for (Map.Entry<String, Float> entry : weights.entrySet()) {
            Optional<UserInterestModel> existing =
                    userInterestModelRepository.findByUserIdAndInterestKey(userId, entry.getKey());
            UserInterestModel model = existing.orElseGet(UserInterestModel::new);
            model.setUserId(userId);
            model.setInterestKey(entry.getKey());
            model.setWeight(entry.getValue());
            model.setEventCount((model.getEventCount() == null ? 0 : model.getEventCount()) + appliedEvents);
            userInterestModelRepository.save(model);
        }
    }

    private Map<String, Float> readWeightsFromRedis(Long userId) {
        Map<String, Float> weights = new LinkedHashMap<>();
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(userModelKey(userId));
        if (entries == null || entries.isEmpty()) {
            entries = stringRedisTemplate.opsForHash().entries(typeRatiosCacheKey(userId));
        }
        if (entries == null || entries.isEmpty()) {
            return weights;
        }
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            try {
                weights.put(normalizeInterestKey(entry.getKey().toString()), Float.parseFloat(entry.getValue().toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return weights;
    }

    private List<TypeRatio> getTypeRatiosFromRedis(Long userId) {
        Map<String, Float> weights = readWeightsFromRedis(userId);
        if (weights.isEmpty()) {
            return null;
        }
        normalize(weights);
        return weightsToRatios(weights);
    }

    private void cacheWeightsInRedis(Long userId, Map<String, Float> weights) {
        if (weights == null || weights.isEmpty()) {
            return;
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : weights.entrySet()) {
            entries.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        stringRedisTemplate.opsForHash().putAll(userModelKey(userId), entries);
        stringRedisTemplate.expire(userModelKey(userId), VECTOR_TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForHash().putAll(typeRatiosCacheKey(userId), entries);
        stringRedisTemplate.expire(typeRatiosCacheKey(userId), VECTOR_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 每次请求续期向量 TTL，与 Sa-Token active-timeout 同步
     */
    public void renewTtl(Long userId) {
        if (userId == null) return;
        stringRedisTemplate.expire(userModelKey(userId), VECTOR_TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.expire(typeRatiosCacheKey(userId), VECTOR_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 用户登录时：从 DB 强制加载兴趣模型到 Redis，保证与 DB 一致
     */
    public void loadToRedis(Long userId) {
        if (userId == null) return;
        loadTypeRatiosFromDb(userId);
        log.debug("[vector] loaded to redis userId={}", userId);
    }

    /**
     * 用户登出时：从 Redis 删除兴趣模型（释放内存）
     */
    public void removeFromRedis(Long userId) {
        if (userId == null) return;
        stringRedisTemplate.delete(userModelKey(userId));
        stringRedisTemplate.delete(typeRatiosCacheKey(userId));
        evictTypeRatiosCache(userId);
        log.debug("[vector] removed from redis userId={}", userId);
    }

    private void cacheTypeRatiosInRedis(Long userId, List<TypeRatio> ratios) {
        Map<String, Float> weights = new LinkedHashMap<>();
        for (TypeRatio ratio : ratios) {
            weights.put(ratio.type().getTypeName(), ratio.ratio());
        }
        cacheWeightsInRedis(userId, weights);
    }

    @SuppressWarnings("unchecked")
    private List<TypeRatio> getTypeRatiosFromLocalCache(Long userId) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_USER_INTEREST_RATIOS);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(userId);
        if (wrapper == null || !(wrapper.get() instanceof List<?>)) {
            return null;
        }
        return (List<TypeRatio>) wrapper.get();
    }

    private void cacheTypeRatiosLocally(Long userId, List<TypeRatio> ratios) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_USER_INTEREST_RATIOS);
        if (cache != null) {
            cache.put(userId, ratios);
        }
    }

    private List<TypeRatio> weightsToRatios(Map<String, Float> weights) {
        List<TypeRatio> ratios = new ArrayList<>();
        for (PostingType type : PostingType.values()) {
            ratios.add(new TypeRatio(type, weights.getOrDefault(type.getTypeName(), 0f)));
        }
        ratios.sort(Comparator.comparingInt(r -> r.type().getIndex()));
        return ratios;
    }

    private Map<String, Float> defaultWeights() {
        Map<String, Float> weights = new LinkedHashMap<>();
        float value = defaultRatio();
        for (PostingType type : PostingType.values()) {
            weights.put(type.getTypeName(), value);
        }
        return weights;
    }

    private List<TypeRatio> getDefaultRatios() {
        return weightsToRatios(defaultWeights());
    }

    private void normalize(Map<String, Float> weights) {
        float sum = 0f;
        for (Float value : weights.values()) {
            if (value != null && value > 0f) {
                sum += value;
            }
        }
        if (sum <= 0f) {
            weights.clear();
            weights.putAll(defaultWeights());
            return;
        }
        for (Map.Entry<String, Float> entry : new ArrayList<>(weights.entrySet())) {
            float value = entry.getValue() == null || entry.getValue() <= 0f ? 0f : entry.getValue();
            weights.put(entry.getKey(), value / sum);
        }
    }

    private String normalizeInterestKey(String rawKey) {
        PostingType type = PostingType.fromString(rawKey);
        return type == null ? rawKey : type.getTypeName();
    }

    private float defaultRatio() {
        return 1f / PostingType.values().length;
    }

    private String userModelKey(Long userId) {
        return USER_MODEL_KEY_PREFIX + userId;
    }

    private String typeRatiosCacheKey(Long userId) {
        return TYPE_RATIOS_CACHE_KEY_PREFIX + userId;
    }

    public record TypeRatio(PostingType type, float ratio) {
        public String typeName() {
            return type.getTypeName();
        }
    }
}
