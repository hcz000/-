package com.example.demo.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            HttpMessageNotReadableException.class
    })
    public SaResult handleBadRequest(Exception ex) {
        log.warn("请求参数错误: {}", ex.getMessage());
        SaResult result = new SaResult();
        result.setCode(400);
        result.setMsg("请求参数错误");
        return result;
    }

    @ExceptionHandler(Exception.class)
    public SaResult handleException(Exception ex) {
        log.error("系统异常", ex);
        SaResult result = new SaResult();
        result.setCode(500);
        result.setMsg("系统繁忙，请稍后再试");
        return result;
    }
}
