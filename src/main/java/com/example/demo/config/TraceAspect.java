package com.example.demo.config;

import com.example.demo.exception.BusinessException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 链路追踪切面
 * <p>
 * 自动记录 Service 层和 Mapper 层的方法调用耗时
 */
@Aspect
@Component
public class TraceAspect {

    private final Tracer tracer;

    public TraceAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Service 层方法追踪
     */
    @Around("execution(* com.example.demo.service..*.*(..))")
    public Object traceServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceMethod(joinPoint, "service");
    }

    /**
     * Mapper 层方法追踪
     */
    @Around("execution(* com.example.demo.mapper..*.*(..))")
    public Object traceMapperMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceMethod(joinPoint, "mapper");
    }

    /**
     * 通用方法追踪
     */
    private Object traceMethod(ProceedingJoinPoint joinPoint, String type) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String spanName = type + ":" + className + "." + methodName;

        // 创建子 Span
        Span span = tracer.nextSpan().name(spanName);
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            long startTime = System.currentTimeMillis();

            Object result = joinPoint.proceed();

            long costTime = System.currentTimeMillis() - startTime;
            // 只记录耗时超过100ms的方法，减少日志量
            if (costTime > 100) {
                Logger log = LoggerFactory.getLogger(joinPoint.getTarget().getClass());
                log.info("[{}] 执行完成, 耗时: {}ms", spanName, costTime);
            }

            // 记录耗时到 span tag
            span.tag("cost_ms", String.valueOf(costTime));
            span.tag("class", className);
            span.tag("method", methodName);

            return result;
        } catch (Throwable e) {
            Logger log = LoggerFactory.getLogger(joinPoint.getTarget().getClass());
            if (e instanceof BusinessException) {
                log.warn("[{}] 业务异常: {}", spanName, e.getMessage());
            } else {
                log.error("[{}] 执行异常: {}", spanName, e.getMessage());
            }
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}