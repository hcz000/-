package com.example.demo.exception;

/**
 * 自定义业务异常，交由全局异常处理器统一处理。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
