package com.example.demo.enums;

public enum LikeBizType {
    POST("post"),
    PRIMARY_COMMENT("primary-comment"),
    SECONDARY_COMMENT("secondary-comment");

    private final String code;

    LikeBizType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static LikeBizType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code为空");
        }
        for (LikeBizType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知code: " + code);
    }
}
