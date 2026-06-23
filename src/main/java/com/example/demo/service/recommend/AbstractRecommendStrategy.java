package com.example.demo.service.recommend;

import com.example.demo.entity.Postings;
import com.example.demo.service.CandidatePoolService;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;

/**
 * 推荐策略抽象基类
 * <p>
 * 候选池的读写委托给 {@link CandidatePoolService}，
 * 帖子发布时入池、删除时出池，Key 按日期自然过期，无需定时重建。
 */
public abstract class AbstractRecommendStrategy implements RecommendStrategy {

    @Resource
    protected CandidatePoolService candidatePoolService;

    // ========== 候选池读取 ==========

    /**
     * 从日榜随机抽取
     */
    protected List<Long> getRandomFromDaily(int count) {
        return candidatePoolService.getRandomFromDaily(count);
    }

    /**
     * 从周榜随机抽取
     */
    protected List<Long> getRandomFromWeekly(int count) {
        return candidatePoolService.getRandomFromWeekly(count);
    }

    /**
     * 从类型池随机抽取（Pipeline 合并最近 7 天，再随机取 count 条）
     */
    protected List<Long> getRandomByType(String typeName, int count) {
        List<Long> allIds = candidatePoolService.getIdsByType(typeName, 7);
        if (allIds.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.shuffle(allIds);
        return allIds.subList(0, Math.min(count, allIds.size()));
    }

    // ========== Common Spec ==========

    protected Specification<Postings> baseSpec() {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            predicate = cb.and(predicate, cb.equal(root.get("status"), 1));
            predicate = cb.and(predicate, cb.equal(root.get("auditStatus"), 1));
            return predicate;
        };
    }
}
