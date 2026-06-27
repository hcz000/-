package com.example.cloud.push.service.recommend;

import com.example.cloud.push.service.CandidatePoolService;
import jakarta.annotation.Resource;

import java.util.Collections;
import java.util.List;

/**
 * 推荐策略抽象基类
 * <p>
 * 候选池的读写委托给 {@link CandidatePoolService}。
 */
public abstract class AbstractRecommendStrategy implements RecommendStrategy {

    @Resource
    protected CandidatePoolService candidatePoolService;

    protected List<Long> getRandomFromDaily(int count) {
        return candidatePoolService.getRandomFromDaily(count);
    }

    protected List<Long> getRandomFromWeekly(int count) {
        return candidatePoolService.getRandomFromWeekly(count);
    }

    protected List<Long> getRandomByType(String typeName, int count) {
        List<Long> allIds = candidatePoolService.getIdsByType(typeName, 7);
        if (allIds.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.shuffle(allIds);
        return allIds.subList(0, Math.min(count, allIds.size()));
    }
}
