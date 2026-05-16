package com.example.demo.task;


import com.example.demo.enums.LikeBizType;

public final class LikeKeyConstants {

    private static final String SET_PREFIX = "like:set:";
    private static final String RANK_PREFIX = "like:rank:";
    private static final String USER_STATE_PREFIX = "like:user:state:";
    private static final String USER_DIRTY_KEY = "like:user:dirty";
    private static final String USER_LIKE_LIST_PREFIX = "like:user:list:";

    private LikeKeyConstants() {
    }

    public static String setKey(LikeBizType type, String bizId) {
        return SET_PREFIX + type.getCode() + ":" + bizId;
    }

    public static String rankKey(LikeBizType type) {
        return RANK_PREFIX + type.getCode();
    }

    public static String userStateKey(LikeBizType type, Long bizId, Long userId) {
        return USER_STATE_PREFIX + type.getCode() + ":" + bizId + ":" + userId;
    }

    public static String userDirtyKey() {
        return USER_DIRTY_KEY;
    }

    /**
     * 用户点赞列表 key（存储用户点赞过的 bizId 集合）
     * 格式：like:user:list:{bizType}:{userId}
     */
    public static String userLikeListKey(LikeBizType type, Long userId) {
        return USER_LIKE_LIST_PREFIX + type.getCode() + ":" + userId;
    }
}
