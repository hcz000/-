package com.example.cloud.push.service;

import com.example.cloud.push.enums.PostingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户兴趣向量服务 —— STUB 版。
 * <p>
 * 单体版的真实实现依赖 {@code UserInterestModel} 实体、Caffeine 二级缓存、
 * 事件驱动的滑动平均更新等，约 385 行代码。
 * <p>
 * 当前微服务骨架阶段：
 * <ul>
 *   <li>{@link #isStableStage(Long)} 始终返回 true，避免阻塞 push.a 走兴趣推荐</li>
 *   <li>{@link #getTypeRatios(Long)} 返回均匀分布的兴趣比例</li>
 * </ul>
 * TODO 后续把真实实现迁过来，或拆为独立的「画像服务」。
 */
@Slf4j
@Service
public class UserVectorService {

    /**
     * 用户兴趣向量是否进入「稳定阶段」。
     * 单体版根据事件数 vs 阈值判断；当前 stub 始终返回 true。
     */
    public boolean isStableStage(Long userId) {
        return true;
    }

    /**
     * 用户的类型偏好比例。
     * 当前 stub：均匀分布，每种类型 12.5%。
     */
    public List<TypeRatio> getTypeRatios(Long userId) {
        float uniform = 1.0f / PostingType.values().length;
        return java.util.Arrays.stream(PostingType.values())
                .map(t -> new TypeRatio(t, uniform))
                .toList();
    }

    public record TypeRatio(PostingType type, float ratio) {
        public String typeName() {
            return type.getTypeName();
        }
    }
}
