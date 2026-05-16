package com.example.demo.enums;

public enum NotificationType {
    POST_COMMENT(1, "帖子评论"),
    COMMENT_REPLY(2, "评论回复"),
    COMMENT_MENTION(3, "@提醒"),
    POST_LIKE(4, "帖子点赞"),
    FRIEND_REQUEST_ACCEPTED(5, "好友申请通过"),
    FRIEND_REQUEST_REJECTED(6, "好友申请被拒绝"),
    PLANET_NEW_POST(7, "星球新帖子"),
    SYSTEM(99, "系统通知");

    private final int code;
    private final String title;

    NotificationType(int code, String title) {
        this.code = code;
        this.title = title;
    }

    public int getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public static NotificationType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (NotificationType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
