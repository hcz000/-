package com.example.demo.service.recommend;

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

        // 超采，再过滤
        List<Long> daily = getRandomFromDaily(dailyCount * OVERSAMPLE_FACTOR);
        List<Long> weekly = getRandomFromWeekly(weeklyCount * OVERSAMPLE_FACTOR);

        List<Long> mergedIds = new ArrayList<>(count + 2);
        Set<Long> seen = new HashSet<>(count * 2);

        // 日榜先填 dailyCount 条
        for (Long id : daily) {
            if (id == null || exclude.contains(id) || !seen.add(id)) continue;
            mergedIds.add(id);
            if (mergedIds.size() >= dailyCount) break;
        }
        // 周榜补到 count 条
        for (Long id : weekly) {
            if (id == null || exclude.contains(id) || !seen.add(id)) continue;
            mergedIds.add(id);
            if (mergedIds.size() >= count) break;
        }
        // 实在不够再从日榜剩余里抽
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
