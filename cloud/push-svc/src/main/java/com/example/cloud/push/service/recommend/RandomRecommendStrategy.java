package com.example.cloud.push.service.recommend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 随机推荐策略（兜底策略）
 * 从日榜取 4 条 + 周榜取 6 条
 */
@Slf4j
@Component("randomStrategy")
public class RandomRecommendStrategy extends AbstractRecommendStrategy {

    private static final int OVERSAMPLE_FACTOR = 3;

    @Override
    public String getName() {
        return "random";
    }

    @Override
    public StrategyResult recommend(Long userId, int count) {
        return recommend(userId, count, Collections.emptySet());
    }

    @Override
    public StrategyResult recommend(Long userId, int count, Set<Long> excludeIds) {
        Set<Long> exclude = excludeIds == null ? Collections.emptySet() : excludeIds;
        int dailyCount = Math.max(1, count * 4 / 10);
        int weeklyCount = count - dailyCount;

        List<Long> daily = getRandomFromDaily(dailyCount * OVERSAMPLE_FACTOR);
        List<Long> weekly = getRandomFromWeekly(weeklyCount * OVERSAMPLE_FACTOR);

        List<Long> mergedIds = new ArrayList<>(count + 2);
        Set<Long> seen = new HashSet<>(count * 2);

        for (Long id : daily) {
            if (id == null || exclude.contains(id) || !seen.add(id)) continue;
            mergedIds.add(id);
            if (mergedIds.size() >= dailyCount) break;
        }
        for (Long id : weekly) {
            if (id == null || exclude.contains(id) || !seen.add(id)) continue;
            mergedIds.add(id);
            if (mergedIds.size() >= count) break;
        }
        if (mergedIds.size() < count) {
            for (Long id : daily) {
                if (id == null || exclude.contains(id) || !seen.add(id)) continue;
                mergedIds.add(id);
                if (mergedIds.size() >= count) break;
            }
        }

        log.debug("[strategy:random] requested={}, returned={}, exclude={}",
                count, mergedIds.size(), exclude.size());
        return StrategyResult.of(mergedIds, getName());
    }
}
