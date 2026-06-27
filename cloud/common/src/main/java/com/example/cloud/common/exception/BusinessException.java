package com.example.cloud.common.exception;

/**
 * 业务异常，由 {@link com.example.cloud.common.exception.GlobalExceptionHandler}
 * 或各服务自己的 ControllerAdvice 统一处理为 4xx 响应。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
