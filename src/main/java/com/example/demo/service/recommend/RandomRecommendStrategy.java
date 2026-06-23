package com.example.demo.service.recommend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 随机推荐策略（兜底策略）
 * 从日榜取 4 条 + 周榜取 6 条
 */
@Slf4j
@Component("randomStrategy")
public class RandomRecommendStrategy extends AbstractRecommendStrategy {

    @Override
    public String getName() {
        return "random";
    }

    @Override
    public StrategyResult recommend(Long userId, int count) {
        int dailyCount = Math.max(1, count * 4 / 10);
        int weeklyCount = count - dailyCount;
        List<Long> mergedIds = new ArrayList<>(count + 2);
        mergedIds.addAll(getRandomFromDaily(dailyCount));
        mergedIds.addAll(getRandomFromWeekly(weeklyCount));
        return StrategyResult.of(mergedIds, getName());
    }
}
