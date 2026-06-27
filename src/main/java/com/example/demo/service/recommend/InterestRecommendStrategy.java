package com.example.demo.service.recommend;

import com.example.demo.service.UserVectorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于用户兴趣向量的加权随机推荐策略
 * 按用户对各类型的兴趣比例分配推荐名额
 */
@Slf4j
@Component("interestStrategy")
public class InterestRecommendStrategy extends AbstractRecommendStrategy {

    /** 候选超采系数：从池子里多捞几倍，过滤掉已曝光后仍能凑够 N 条 */
    private static final int OVERSAMPLE_FACTOR = 3;

    @Resource
    private UserVectorService userVectorService;

    @Override
    public String getName() {
        return "interest_vector";
    }

    @Override
    public StrategyResult recommend(Long userId, int count) {
        return recommend(userId, count, Collections.emptySet());
    }

    @Override
    public StrategyResult recommend(Long userId, int count, Set<Long> excludeIds) {
        if (userId == null) {
            return StrategyResult.of(List.of(), getName());
        }
        Set<Long> exclude = excludeIds == null ? Collections.emptySet() : excludeIds;

        List<UserVectorService.TypeRatio> ratios = userVectorService.getTypeRatios(userId);
        log.info("[strategy:interest] userId={} ratios={}",
                userId, ratios.stream().map(r -> r.typeName() + "=" + (int) (r.ratio() * 100) + "%").toList());

        List<Long> mergedIds = new ArrayList<>(count * 2);
        Set<Long> seenIds = new HashSet<>(count * 2);

        // 按兴趣比例分配名额
        List<Integer> typeCounts = new ArrayList<>();
        int interestCount = 0;
        for (UserVectorService.TypeRatio ratio : ratios) {
            int c = (int) (ratio.ratio() * count);
            typeCounts.add(c);
            interestCount += c;
        }
        log.info("[strategy:interest] interestAlloc={}, randomFill={}, exclude={}",
                interestCount, count - interestCount, exclude.size());

        // 从各类型池中抽取（超采 + 排除已曝光）
        for (int i = 0; i < ratios.size(); i++) {
            UserVectorService.TypeRatio ratio = ratios.get(i);
            int c = typeCounts.get(i);
            if (c <= 0) continue;

            List<Long> typeIds = getRandomByType(ratio.type().getTypeName(), c * OVERSAMPLE_FACTOR);
            int picked = 0;
            for (Long id : typeIds) {
                if (id == null || exclude.contains(id)) continue;
                if (seenIds.add(id)) {
                    mergedIds.add(id);
                    if (++picked >= c) break;
                }
            }
        }

        // 不足时用周榜随机补齐（同样超采 + 排除）
        int remaining = count - mergedIds.size();
        if (remaining > 0) {
            List<Long> randomIds = getRandomFromWeekly(remaining * OVERSAMPLE_FACTOR);
            for (Long id : randomIds) {
                if (id == null || exclude.contains(id)) continue;
                if (seenIds.add(id) && mergedIds.size() < count) {
                    mergedIds.add(id);
                }
            }
        }

        log.debug("[strategy:interest] userId={}, requested={}, returned={}, exclude={}",
                userId, count, mergedIds.size(), exclude.size());
        return StrategyResult.of(mergedIds, getName());
    }
}
