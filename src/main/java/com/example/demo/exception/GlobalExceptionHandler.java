package com.example.demo.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public SaResult handleBusinessException(BusinessException ex) {
        String msg = ex.getMessage() == null ? "业务异常" : ex.getMessage();
        SaResult result = new SaResult();
        result.setMsg(msg);

        if (msg.contains("不存在") || msg.toLowerCase().contains("not found")) {
            result.setCode(404);
            return result;
        }

        result.setCode(400);
        return result;
    }

    @ExceptionHandler(NotLoginException.class)
    public SaResult handleNotLoginException(NotLoginException ex) {
        log.debug("用户未登录: {}", ex.getMessage());
        SaResult result = new SaResult();
        result.setCode(401);
        result.setMsg("用户未登录");
        return result;
    }

    /**
     * 处理 @Valid 校验失败异常
     * 返回详细的字段错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public SaResult handleValidationException(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        
        // 收集所有字段错误信息
        Map<String, String> errorDetails = fieldErrors.stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "参数校验失败",
                        (existing, replacement) -> existing // 保留第一个错误
                ));
        
        // 构建友好的错误消息
        String errorMessage = fieldErrors.stream()
                .map(error -> {
                    String field = error.getField();
                    String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "参数校验失败";
                    return field + ": " + message;
                })
                .collect(Collectors.joining(", "));
        
        log.warn("请求参数校验失败: {}", errorMessage);
        
        SaResult result = new SaResult();
        result.setCode(400);
        result.setMsg("参数校验失败: " + errorMessage);
        result.setData(errorDetails); // 返回详细的字段错误信息
        return result;
    }

    /**
     * 处理 @Validated 校验失败异常(GET请求参数校验)
     */
    @ExceptionHandler(BindException.class)
    public SaResult handleBindException(BindException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        
        String errorMessage = fieldErrors.stream()
                .map(error -> {
                    String field = error.getField();
                    String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "参数校验失败";
                    return field + ": " + message;
                })
                .collect(Collectors.joining(", "));
        
        log.warn("请求参数绑定失败: {}", errorMessage);
        
        SaResult result = new SaResult();
        result.setCode(400);
        result.setMsg("参数错误: " + errorMessage);
        return result;
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public SaResult handleMissingParamException(MissingServletRequestParameterException ex) {
        String paramName = ex.getParameterName();
        String paramType = ex.getParameterType();
        log.warn("缺少请求参数: {} ({})", paramName, paramType);
        
        SaResult result = new SaResult();
        result.setCode(400);
        result.setMsg("缺少必要参数: " + paramName);
        return result;
    }

    /**
     * 处理请求参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public SaResult handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String paramName = ex.getName();
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "未知";
        Object value = ex.getValue();
        log.warn("参数类型不匹配: {} (期望类型: {}, 实际值: {})", paramName, requiredType, value);
        
        SaResult result = new SaResult();
        result.setCode(400);
        result.setMsg("参数类型错误: " + paramName + " 应该为 " + requiredType + " 类型");
        return result;
    }

    /**
     * 处理请求体无法读取异常(JSON格式错误)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public SaResult handleMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        
        SaResult result = new SaResult();
        result.setCode(400);
        result.setMsg("请求格式错误,请检查JSON格式");
        return result;
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public SaResult handleException(Exception ex) {
        log.error("系统异常", ex);
        SaResult result = new SaResult();
        result.setCode(500);
        result.setMsg("系统繁忙,请稍后再试");
        return result;
    }
}
