package com.example.cloud.post.dto;

import com.example.cloud.post.enums.LikeBizType;

/**
 * 点赞计数聚合后发到 MQ 的消息（聚合维度：bizType+bizId，delta 是 net change）。
 */
public class LikeSyncMessage {

    private final LikeBizType bizType;
    private final Long bizId;
    private final int delta;

    public LikeSyncMessage(LikeBizType bizType, Long bizId, int delta) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.delta = delta;
    }

    public LikeBizType getBizType() { return bizType; }
    public Long getBizId() { return bizId; }
    public int getDelta() { return delta; }

    public String serialize() {
        return bizType.name() + "|" + bizId + "|" + delta;
    }

    public static LikeSyncMessage deserialize(String payload) {
        if (payload == null || payload.isEmpty()) throw new IllegalArgumentException("载荷不能为空");
        String[] parts = payload.split("\\|");
        if (parts.length != 3) throw new IllegalArgumentException("载荷格式无效");
        LikeBizType type = LikeBizType.valueOf(parts[0]);
        long bizId = Long.parseLong(parts[1]);
        int value = Integer.parseInt(parts[2]);
        return new LikeSyncMessage(type, bizId, value);
    }
}
