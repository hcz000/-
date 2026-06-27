package com.example.cloud.user.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
        return new SaResult().setCode(401).setMsg("用户未登录");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public SaResult handleValidationException(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        Map<String, String> errorDetails = fieldErrors.stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "参数校验失败",
                        (existing, replacement) -> existing));
        String errorMessage = fieldErrors.stream()
                .map(error -> error.getField() + ": "
                        + (error.getDefaultMessage() != null ? error.getDefaultMessage() : "参数校验失败"))
                .collect(Collectors.joining(", "));
        log.warn("请求参数校验失败: {}", errorMessage);
        return new SaResult().setCode(400).setMsg("参数校验失败: " + errorMessage).setData(errorDetails);
    }

    @ExceptionHandler(BindException.class)
    public SaResult handleBindException(BindException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": "
                        + (error.getDefaultMessage() != null ? error.getDefaultMessage() : "参数校验失败"))
                .collect(Collectors.joining(", "));
        log.warn("请求参数绑定失败: {}", errorMessage);
        return new SaResult().setCode(400).setMsg("参数错误: " + errorMessage);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public SaResult handleMissingParamException(MissingServletRequestParameterException ex) {
        log.warn("缺少请求参数: {} ({})", ex.getParameterName(), ex.getParameterType());
        return new SaResult().setCode(400).setMsg("缺少必要参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public SaResult handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "未知";
        log.warn("参数类型不匹配: {} (期望类型: {}, 实际值: {})", ex.getName(), requiredType, ex.getValue());
        return new SaResult().setCode(400).setMsg("参数类型错误: " + ex.getName() + " 应该为 " + requiredType + " 类型");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public SaResult handleMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return new SaResult().setCode(400).setMsg("请求格式错误,请检查JSON格式");
    }

    @ExceptionHandler(Exception.class)
    public SaResult handleException(Exception ex) {
        log.error("系统异常", ex);
        return new SaResult().setCode(500).setMsg("系统繁忙,请稍后再试");
    }
}
