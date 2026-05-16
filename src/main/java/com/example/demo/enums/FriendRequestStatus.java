package com.example.demo.enums;

public enum FriendRequestStatus {
    PENDING(0),
    ACCEPTED(1),
    REJECTED(2);

    private final int code;

    FriendRequestStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
