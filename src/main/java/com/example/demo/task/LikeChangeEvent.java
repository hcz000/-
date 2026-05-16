package com.example.demo.task;

import com.example.demo.enums.LikeBizType;

/**
 * 点赞变更事件
 * 用于记录单次点赞操作的变更信息
 */
public class LikeChangeEvent {

    private final LikeBizType bizType;
    private final Long bizId;
    private final Long userId;
    private final boolean liked;  // true: 点赞, false: 取消点赞
    private final long timestamp;

    public LikeChangeEvent(LikeBizType bizType, Long bizId, Long userId, boolean liked) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.userId = userId;
        this.liked = liked;
        this.timestamp = System.currentTimeMillis();
    }

    public LikeBizType getBizType() {
        return bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isLiked() {
        return liked;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 生成聚合 Key（bizType:bizId）
     */
    public String aggregationKey() {
        return bizType.getCode() + ":" + bizId;
    }

    /**
     * 生成用户状态 Key（bizType:bizId:userId）
     */
    public String userStateKey() {
        return bizType.getCode() + ":" + bizId + ":" + userId;
    }

    @Override
    public String toString() {
        return "LikeChangeEvent{" +
                "bizType=" + bizType +
                ", bizId=" + bizId +
                ", userId=" + userId +
                ", liked=" + liked +
                ", timestamp=" + timestamp +
                '}';
    }
}