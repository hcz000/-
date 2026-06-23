package com.example.demo.service.recommend;

import java.util.List;

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
     * 策略名称，用于日志和运行时识别
     */
    String getName();

    /**
     * 推荐结果
     */
    record StrategyResult(List<Long> postIds, String strategyName) {
        public static StrategyResult of(List<Long> postIds, String strategyName) {
            return new StrategyResult(postIds, strategyName);
        }
    }
}
