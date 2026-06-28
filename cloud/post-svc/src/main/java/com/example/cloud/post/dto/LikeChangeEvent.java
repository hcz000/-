package com.example.cloud.post.dto;

import com.example.cloud.post.enums.LikeBizType;

/**
 * 点赞变更事件（内存中的 event，buffer 聚合用）。
 */
public class LikeChangeEvent {

    private final LikeBizType bizType;
    private final Long bizId;
    private final Long userId;
    private final boolean liked;
    private final long timestamp;

    public LikeChangeEvent(LikeBizType bizType, Long bizId, Long userId, boolean liked) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.userId = userId;
        this.liked = liked;
        this.timestamp = System.currentTimeMillis();
    }

    public LikeBizType getBizType() { return bizType; }
    public Long getBizId() { return bizId; }
    public Long getUserId() { return userId; }
    public boolean isLiked() { return liked; }
    public long getTimestamp() { return timestamp; }

    public String aggregationKey() {
        return bizType.getCode() + ":" + bizId;
    }

    public String userStateKey() {
        return bizType.getCode() + ":" + bizId + ":" + userId;
    }
}
