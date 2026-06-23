package com.example.demo.enums;

public enum FriendRequestStatus {
    PENDING(0),// 待处理
    ACCEPTED(1),// 已接受
    REJECTED(2);// 已拒绝

    private final int code;

    FriendRequestStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
