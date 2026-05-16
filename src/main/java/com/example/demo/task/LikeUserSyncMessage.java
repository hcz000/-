package com.example.demo.task;


import com.example.demo.enums.LikeBizType;

public class LikeUserSyncMessage {

    private final LikeBizType bizType;
    private final Long bizId;
    private final Long userId;
    private final boolean liked;

    public LikeUserSyncMessage(LikeBizType bizType, Long bizId, Long userId, boolean liked) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.userId = userId;
        this.liked = liked;
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

    public String serialize() {
        return bizType.name() + "|" + bizId + "|" + userId + "|" + (liked ? 1 : 0);
    }

    public static LikeUserSyncMessage deserialize(String payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("payload is empty");
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("payload format invalid");
        }
        LikeBizType type = LikeBizType.valueOf(parts[0]);
        long bizId = Long.parseLong(parts[1]);
        long userId = Long.parseLong(parts[2]);
        boolean liked = "1".equals(parts[3]) || "true".equalsIgnoreCase(parts[3]);
        return new LikeUserSyncMessage(type, bizId, userId, liked);
    }
}
