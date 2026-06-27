package com.example.cloud.push.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public SaResult handleBusinessException(BusinessException ex) {
        String msg = ex.getMessage() == null ? "业务异常" : ex.getMessage();
        return new SaResult().setCode(400).setMsg(msg);
    }

    @ExceptionHandler(NotLoginException.class)
    public SaResult handleNotLoginException(NotLoginException ex) {
        log.debug("用户未登录: {}", ex.getMessage());
        return new SaResult().setCode(401).setMsg("用户未登录");
    }

    @ExceptionHandler(Exception.class)
    public SaResult handleException(Exception ex) {
        log.error("[push] 系统异常", ex);
        return new SaResult().setCode(500).setMsg("系统繁忙,请稍后再试");
    }
}
