package com.example.cloud.push.service;

import com.example.cloud.push.entity.UserInterestModel;
import com.example.cloud.push.enums.PostingType;
import com.example.cloud.push.repository.UserInterestModelRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户兴趣向量服务（真实实现，简化版）。
 * <p>
 * 与单体版的差异（保留核心算法）：
 * <ul>
 *   <li>滑动平均维护 weight（每次事件按 alpha 比例混入）</li>
 *   <li>event_count 累加，达到阈值认为进入稳定阶段</li>
 *   <li>简化点：去掉 Caffeine + Redis 二级缓存，每次查 DB（演示规模够用）</li>
 *   <li>简化点：去掉 user:vector:ratios 预算缓存（同上）</li>
 * </ul>
 *
 * <h3>事件接入</h3>
 * 当前没有事件源（post-svc 的点赞/评论事件未发到 push-svc）。
 * 后续扩展时由 post-svc 发 UserBehaviorEvent，
 * push-svc 消费后调 {@link #updateUserVector}。
 */
@Slf4j
@Service
public class UserVectorService {

    /** 滑动平均系数 */
    @Value("${app.vector.alpha:0.1}")
    private float alpha;

    /** 进入稳定阶段的事件阈值 */
    @Value("${app.vector.stable-threshold:5}")
    private int stableThreshold;

    @Resource
    private UserInterestModelRepository userInterestModelRepository;

    /**
     * 用户对某类型的行为事件 → 更新该用户的兴趣权重。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUserVector(Long userId, PostingType postingType) {
        if (userId == null || postingType == null) return;
        String key = postingType.name();
        UserInterestModel model = userInterestModelRepository
                .findByUserIdAndInterestKey(userId, key)
                .orElseGet(() -> {
                    UserInterestModel m = new UserInterestModel();
                    m.setUserId(userId);
                    m.setInterestKey(key);
                    m.setWeight(0f);
                    m.setEventCount(0);
                    return m;
                });
        // 滑动平均：new = (1 - alpha) * old + alpha * 1.0
        float newWeight = (1 - alpha) * model.getWeight() + alpha * 1.0f;
        model.setWeight(newWeight);
        model.setEventCount(model.getEventCount() + 1);
        model.setUpdateTime(LocalDateTime.now());
        userInterestModelRepository.save(model);
    }

    /**
     * 用户是否进入「兴趣稳定阶段」。
     * 算法：所有类型的事件总数超过阈值。
     */
    public boolean isStableStage(Long userId) {
        if (userId == null) return false;
        List<UserInterestModel> rows = userInterestModelRepository.findByUserId(userId);
        int total = rows.stream().mapToInt(UserInterestModel::getEventCount).sum();
        return total >= stableThreshold;
    }

    /**
     * 用户的类型偏好比例（每个类型一行，未出现的类型按 0 占比）。
     */
    public List<TypeRatio> getTypeRatios(Long userId) {
        if (userId == null) return getDefaultRatios();
        List<UserInterestModel> rows = userInterestModelRepository.findByUserId(userId);
        if (rows.isEmpty()) return getDefaultRatios();

        // 总权重
        float total = (float) rows.stream().mapToDouble(UserInterestModel::getWeight).sum();
        if (total <= 0) return getDefaultRatios();

        List<TypeRatio> result = new ArrayList<>(PostingType.values().length);
        for (PostingType type : PostingType.values()) {
            float weight = rows.stream()
                    .filter(r -> type.name().equals(r.getInterestKey()))
                    .map(UserInterestModel::getWeight)
                    .findFirst()
                    .orElse(0f);
            result.add(new TypeRatio(type, weight / total));
        }
        return result;
    }

    /** 默认均匀分布（冷启动） */
    private List<TypeRatio> getDefaultRatios() {
        float uniform = 1.0f / PostingType.values().length;
        return java.util.Arrays.stream(PostingType.values())
                .map(t -> new TypeRatio(t, uniform))
                .toList();
    }

    /**
     * 批量应用兴趣事件（一批同一用户的 PostingType），返回实际应用条数。
     * 供 UserVectorBufferService 用来高吞吐场景下批处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public int applyUserVectorBatch(Long userId, List<PostingType> postingTypes) {
        if (userId == null || postingTypes == null || postingTypes.isEmpty()) return 0;
        int applied = 0;
        for (PostingType type : postingTypes) {
            if (type == null) continue;
            updateUserVector(userId, type);
            applied++;
        }
        return applied;
    }

    /**
     * 清理本用户的兴趣比例缓存（接口预留；当前简化版没有缓存层，等价于 no-op）。
     */
    public void evictTypeRatiosCache(Long userId) {
        // simplified: no Redis/Caffeine cache layer in this implementation
    }

    public record TypeRatio(PostingType type, float ratio) {
        public String typeName() {
            return type.getTypeName();
        }
    }
}
