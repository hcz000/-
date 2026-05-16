package com.example.demo.service;

import com.example.demo.entity.Postings;
import com.example.demo.entity.UserLike;
import com.example.demo.enums.LikeBizType;
import com.example.demo.enums.PostingType;
import com.example.demo.repository.PostingsRepository;
import com.example.demo.repository.UserLikeRepository;
import com.example.demo.task.LikeUserSyncMessage;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class LikeUserSyncService {

    @Resource
    private UserLikeRepository userLikeRepository;
    @Resource
    private PostingsRepository postingsRepository;
    @Resource
    private UserVectorBufferService userVectorBufferService;

    @Transactional
    public void applyMessage(LikeUserSyncMessage message) {
        validateMessage(message);

        List<UserLike> existingLikes = loadExistingLikes(message);
        boolean wasLiked = existingLikes.stream().anyMatch(item -> Boolean.TRUE.equals(item.getLiked()));
        LocalDateTime now = LocalDateTime.now();

        if (existingLikes.isEmpty()) {
            if (!message.isLiked()) {
                return;
            }
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
                log.debug("Duplicate like ignored: bizType={}, bizId={}, userId={}",
                        message.getBizType(), message.getBizId(), message.getUserId());
            }
        } else if (needsStateRefresh(existingLikes, message.isLiked())) {
            for (UserLike like : existingLikes) {
                like.setLiked(message.isLiked());
                like.setUpdateTime(now);
                userLikeRepository.save(like);
            }
        }

        if (shouldUpdateUserVector(message, wasLiked)) {
            bufferUserInterestVector(message);
        }
    }

    private List<UserLike> loadExistingLikes(LikeUserSyncMessage message) {
        Specification<UserLike> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("bizType"), message.getBizType().getCode()));
            predicate = cb.and(predicate, cb.equal(root.get("bizId"), message.getBizId()));
            predicate = cb.and(predicate, cb.equal(root.get("userId"), message.getUserId()));
            return predicate;
        };
        return userLikeRepository.findAll(spec);
    }

    private boolean needsStateRefresh(List<UserLike> existingLikes, boolean targetLiked) {
        return existingLikes.stream().anyMatch(item -> !Objects.equals(item.getLiked(), targetLiked));
    }

    private boolean shouldUpdateUserVector(LikeUserSyncMessage message, boolean wasLiked) {
        return message.getBizType() == LikeBizType.POST && message.isLiked() && !wasLiked;
    }

    private void bufferUserInterestVector(LikeUserSyncMessage message) {
        Postings post = postingsRepository.findById(message.getBizId()).orElse(null);
        if (post == null || !StringUtils.hasText(post.getType())) {
            log.debug("Skip vector enqueue: post has no type, postId={}", message.getBizId());
            return;
        }

        PostingType postingType = PostingType.fromString(post.getType());
        if (postingType == null) {
            log.debug("Skip vector enqueue: unknown post type, postId={}, type={}", message.getBizId(), post.getType());
            return;
        }

        userVectorBufferService.enqueue(message.getUserId(), postingType);
    }

    private void validateMessage(LikeUserSyncMessage message) {
        if (message == null || message.getBizType() == null || message.getBizId() == null || message.getUserId() == null) {
            throw new IllegalArgumentException("like-user-sync message is invalid");
        }
    }
}