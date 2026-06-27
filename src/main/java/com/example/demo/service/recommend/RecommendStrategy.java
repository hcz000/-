package com.example.demo.service.recommend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 推荐策略接口
 * 不同推荐算法实现此接口，通过配置切换或 AB 测试
 */
public interface RecommendStrategy {

    /**
     * 根据用户 ID 生成推荐帖子 ID 列表
     *
     * @param userId 用户 ID（可能为 null，表示未登录）
     * @param count  推荐数量
     * @return 推荐结果（含帖子 ID 列表和策略名称）
     */
    StrategyResult recommend(Long userId, int count);

    /**
     * 带排除集的推荐：用于「刷新拿下一屏」场景，避免重复推送已看过的帖子。
     * <p>
     * 默认实现走原始 recommend 再做一次内存过滤，作为兜底；具体策略建议
     * 重写为「在候选层就排除」，避免过滤后剩不下 N 条的问题。
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

    /**
     * 策略名称，用于日志和运行时识别
     */
    String getName();

    /**
     * 推荐结果
     */
    record StrategyResult(List<Long> postIds, String strategyName) {
        public static StrategyResult of(List<Long> postIds, String strategyName) {
            return new StrategyResult(postIds == null ? Collections.emptyList() : postIds, strategyName);
        }
    }
}
