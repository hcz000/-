package com.example.cloud.post.service;

import com.example.cloud.post.dto.LikeUserSyncMessage;
import com.example.cloud.post.entity.Postings;
import com.example.cloud.post.entity.UserLike;
import com.example.cloud.post.enums.LikeBizType;
import com.example.cloud.post.repository.PostingsRepository;
import com.example.cloud.post.repository.UserLikeRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 用户点赞落库 + 触发兴趣向量更新。
 * <p>
 * 与单体的差异：UserVectorBufferService 在 push-svc 域内，post-svc 无法直接调。
 * 当前简化为 log + TODO，后续可通过 MQ 发 UserBehaviorEvent 给 push-svc 消费。
 */
@Slf4j
@Service
public class LikeUserSyncService {

    @Resource
    private UserLikeRepository userLikeRepository;

    @Resource
    private PostingsRepository postingsRepository;

    @Transactional(rollbackFor = Exception.class)
    public void applyMessage(LikeUserSyncMessage message) {
        validateMessage(message);

        List<UserLike> existingLikes = loadExistingLikes(message);
        boolean wasLiked = existingLikes.stream().anyMatch(item -> Boolean.TRUE.equals(item.getLiked()));
        LocalDateTime now = LocalDateTime.now();

        if (existingLikes.isEmpty()) {
            if (!message.isLiked()) return;
            UserLike entity = new UserLike();
            entity.setBizType(message.getBizType().getCode());
            entity.setBizId(message.getBizId());
            entity.setUserId(message.getUserId());
            entity.setLiked(true);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            try {
                userLikeRepository.save(entity);
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate like ignored");
            }
        } else if (needsStateRefresh(existingLikes, message.isLiked())) {
            for (UserLike like : existingLikes) {
                like.setLiked(message.isLiked());
                like.setUpdateTime(now);
                userLikeRepository.save(like);
            }
        }

        if (shouldUpdateUserVector(message, wasLiked)) {
            // TODO 跨服务：发 UserBehaviorEvent 给 push-svc 触发兴趣向量更新
            // push-svc 的 UserVectorBufferService.enqueue(userId, postingType) 等效逻辑
            Postings post = postingsRepository.findById(message.getBizId()).orElse(null);
            if (post != null && post.getType() != null) {
                log.info("[like-user-sync] TODO publish UserBehaviorEvent userId={}, postType={}",
                        message.getUserId(), post.getType());
            }
        }
    }

    private List<UserLike> loadExistingLikes(LikeUserSyncMessage message) {
        Specification<UserLike> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("bizType"), message.getBizType().getCode());
            p = cb.and(p, cb.equal(root.get("bizId"), message.getBizId()));
            p = cb.and(p, cb.equal(root.get("userId"), message.getUserId()));
            return p;
        };
        return userLikeRepository.findAll(spec);
    }

    private boolean needsStateRefresh(List<UserLike> existingLikes, boolean targetLiked) {
        return existingLikes.stream().anyMatch(item -> !Objects.equals(item.getLiked(), targetLiked));
    }

    private boolean shouldUpdateUserVector(LikeUserSyncMessage message, boolean wasLiked) {
        return message.getBizType() == LikeBizType.POST && message.isLiked() && !wasLiked;
    }

    private void validateMessage(LikeUserSyncMessage message) {
        if (message == null || message.getBizType() == null
                || message.getBizId() == null || message.getUserId() == null) {
            throw new IllegalArgumentException("like-user-sync message is invalid");
        }
    }
}
