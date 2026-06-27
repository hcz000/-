package com.example.cloud.push.service.recommend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 推荐策略接口
 * 不同推荐算法实现此接口，通过配置切换或 AB 测试
 */
public interface RecommendStrategy {

    StrategyResult recommend(Long userId, int count);

    /**
     * 带排除集的推荐：用于「刷新拿下一屏」场景，避免重复推送已看过的帖子。
     */
    default StrategyResult recommend(Long userId, int count, Set<Long> excludeIds) {
        StrategyResult origin = recommend(userId, count);
        if (excludeIds == null || excludeIds.isEmpty()) {
            return origin;
        }
        List<Long> filtered = new ArrayList<>(origin.postIds().size());
        for (Long id : origin.postIds()) {
            if (id != null && !excludeIds.contains(id)) {
                filtered.add(id);
            }
        }
        return StrategyResult.of(filtered, origin.strategyName());
    }

    String getName();

    record StrategyResult(List<Long> postIds, String strategyName) {
        public static StrategyResult of(List<Long> postIds, String strategyName) {
            return new StrategyResult(postIds == null ? Collections.emptyList() : postIds, strategyName);
        }
    }
}
