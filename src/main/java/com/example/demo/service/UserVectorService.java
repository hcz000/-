package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.enums.PostingType;
import com.example.demo.repository.UserRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserVectorService {

    private static final float STABLE_STAGE_SUM_THRESHOLD = 0.99f;

    @Resource
    private UserRepository userRepository;

    @Transactional
    public void updateUserVector(Long userId, PostingType postingType) {
        if (postingType == null) {
            log.warn("Posting type is null, skip vector update");
            return;
        }
        applyUserVectorBatch(userId, Collections.singletonList(postingType));
    }

    @Transactional
    public int applyUserVectorBatch(Long userId, List<PostingType> postingTypes) {
        if (postingTypes == null || postingTypes.isEmpty()) {
            return 0;
        }

        User user = userRepository.selectByIdForUpdate(userId);
        if (user == null) {
            log.warn("User not found: {}", userId);
            return 0;
        }

        Float[] currentVector = user.getLiketype();
        if (currentVector == null || currentVector.length != PostingType.VECTOR_DIMENSION) {
            float initValue = 1.0f / PostingType.VECTOR_DIMENSION;
            currentVector = new Float[PostingType.VECTOR_DIMENSION];
            Arrays.fill(currentVector, initValue);
        }

        // 统计每种类型的出现次数
        int[] typeCounts = new int[PostingType.VECTOR_DIMENSION];
        int applied = 0;
        for (PostingType postingType : postingTypes) {
            if (postingType == null) {
                continue;
            }
            typeCounts[postingType.getIndex()]++;
            applied++;
        }
        
        if (applied <= 0) {
            return 0;
        }

        // 计算新的兴趣向量：直接累加计数后归一化
        float sum = 0f;
        for (int i = 0; i < PostingType.VECTOR_DIMENSION; i++) {
            currentVector[i] = currentVector[i] + typeCounts[i];
            sum += currentVector[i];
        }

        // 归一化，保证总和为1
        if (sum > 0) {
            for (int i = 0; i < PostingType.VECTOR_DIMENSION; i++) {
                currentVector[i] = currentVector[i] / sum;
            }
        }

        user.setLiketype(currentVector);
        userRepository.save(user);
        log.info("Updated user vector in batch: userId={}, appliedEvents={}", userId, applied);
        return applied;
    }

    public void updateUserVector(Long userId, String postingTypeStr) {
        PostingType postingType = PostingType.fromString(postingTypeStr);
        if (postingType == null) {
            log.warn("Unknown posting type: {}", postingTypeStr);
            return;
        }
        updateUserVector(userId, postingType);
    }

    public List<TypeRatio> getTypeRatios(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("User not found: {}", userId);
            return getDefaultRatios();
        }

        Float[] userVector = user.getLiketype();
        if (userVector == null || userVector.length != PostingType.VECTOR_DIMENSION) {
            return getDefaultRatios();
        }

        float sum = 0f;
        for (Float value : userVector) {
            sum += value;
        }
        if (sum <= 0f) {
            return getDefaultRatios();
        }

        List<TypeRatio> ratios = new ArrayList<>();
        PostingType[] postingTypes = PostingType.values();
        for (int i = 0; i < postingTypes.length; i++) {
            ratios.add(new TypeRatio(postingTypes[i], userVector[i] / sum));
        }
        return ratios;
    }

    public float getVectorSum(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return 0f;
        }

        Float[] userVector = user.getLiketype();
        if (userVector == null || userVector.length != PostingType.VECTOR_DIMENSION) {
            return 0f;
        }

        float sum = 0f;
        for (Float value : userVector) {
            sum += value;
        }
        return Math.max(sum, 0f);
    }

    public boolean isStableStage(Long userId) {
        // 由于现在向量总和始终为1，只要有行为数据就认为是稳定阶段
        return getVectorSum(userId) >= STABLE_STAGE_SUM_THRESHOLD;
    }

    private List<TypeRatio> getDefaultRatios() {
        float defaultRatio = 1f / PostingType.values().length;
        List<TypeRatio> ratios = new ArrayList<>();
        for (PostingType postingType : PostingType.values()) {
            ratios.add(new TypeRatio(postingType, defaultRatio));
        }
        return ratios;
    }

    public record TypeRatio(PostingType type, float ratio) {
        public String typeName() {
            return type.getTypeName();
        }
    }
}
