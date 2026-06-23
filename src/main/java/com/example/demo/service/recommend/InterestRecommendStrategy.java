package com.example.demo.service.recommend;

import com.example.demo.enums.PostingType;
import com.example.demo.service.UserVectorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    @Resource
    private UserVectorService userVectorService;

    @Override
    public String getName() {
        return "interest_vector";
    }

    @Override
    public StrategyResult recommend(Long userId, int count) {
        if (userId == null) {
            return StrategyResult.of(List.of(), getName());
        }

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
        log.info("[strategy:interest] interestAlloc={}, randomFill={}", interestCount, count - interestCount);

        // 从各类型池中抽取
        for (int i = 0; i < ratios.size(); i++) {
            UserVectorService.TypeRatio ratio = ratios.get(i);
            int c = typeCounts.get(i);
            if (c <= 0) continue;

            List<Long> typeIds = getRandomByType(ratio.type().getTypeName(), c);
            for (Long id : typeIds) {
                if (id == null) continue;
                if (seenIds.add(id)) {
                    mergedIds.add(id);
                }
            }
        }

        // 不足时用周榜随机补齐
        int remaining = count - mergedIds.size();
        if (remaining > 0) {
            List<Long> randomIds = getRandomFromWeekly(remaining * 2);
            for (Long id : randomIds) {
                if (id == null) continue;
                if (seenIds.add(id) && mergedIds.size() < count) {
                    mergedIds.add(id);
                }
            }
        }

        return StrategyResult.of(mergedIds, getName());
    }
}
