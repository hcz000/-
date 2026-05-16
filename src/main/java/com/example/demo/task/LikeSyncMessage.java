package com.example.demo.task;


import com.example.demo.enums.LikeBizType;

public class LikeSyncMessage {

    private final LikeBizType bizType;
    private final Long bizId;
    private final int delta;  // 增量：+1 点赞, -1 取消点赞

    public LikeSyncMessage(LikeBizType bizType, Long bizId, int delta) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.delta = delta;
    }

    public LikeBizType getBizType() {
        return bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public int getDelta() {
        return delta;
    }

    public String serialize() {
        return bizType.name() + "|" + bizId + "|" + delta;
    }

    public static LikeSyncMessage deserialize(String payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("载荷不能为空");
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 3) {
            throw new IllegalArgumentException("载荷格式无效");
        }
        LikeBizType type = LikeBizType.valueOf(parts[0]);
        long bizId = Long.parseLong(parts[1]);
        int value = Integer.parseInt(parts[2]);
        return new LikeSyncMessage(type, bizId, value);
    }
}
