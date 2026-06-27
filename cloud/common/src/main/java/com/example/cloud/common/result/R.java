package com.example.cloud.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应体，跨服务调用和对外接口都用这个。
 *
 * @param <T> 数据载荷类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> implements Serializable {

    public static final int CODE_OK = 0;
    public static final int CODE_FAIL = 500;

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return new R<>(CODE_OK, "ok", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(CODE_OK, "ok", data);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(CODE_FAIL, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public boolean isOk() {
        return code == CODE_OK;
    }
}
