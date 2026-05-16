package com.example.demo.util;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 链路追踪工具类
 */
@Component
public class TraceUtil {

    private static Tracer tracer;

    public TraceUtil(Tracer tracer) {
        TraceUtil.tracer = tracer;
    }

    /**
     * 获取当前 TraceId
     */
    public static String getTraceId() {
        // 优先从 Tracer 获取
        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                return currentSpan.context().traceId();
            }
        }
        // 降级从 MDC 获取
        return MDC.get("traceId");
    }

    /**
     * 获取当前 SpanId
     */
    public static String getSpanId() {
        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                return currentSpan.context().spanId();
            }
        }
        return MDC.get("spanId");
    }

    /**
     * 创建新的 Span
     */
    public static Span newSpan(String name) {
        if (tracer == null) {
            return null;
        }
        return tracer.nextSpan().name(name);
    }

    /**
     * 在新 Span 中执行操作
     */
    public static <T> T withSpan(String name, java.util.function.Supplier<T> supplier) {
        if (tracer == null) {
            return supplier.get();
        }
        Span span = newSpan(name);
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            return supplier.get();
        } finally {
            span.end();
        }
    }

    /**
     * 在新 Span 中执行操作（无返回值）
     */
    public static void withSpan(String name, Runnable runnable) {
        if (tracer == null) {
            runnable.run();
            return;
        }
        Span span = newSpan(name);
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            runnable.run();
        } finally {
            span.end();
        }
    }

    /**
     * 添加 Tag 到当前 Span
     */
    public static void tag(String key, String value) {
        if (tracer == null) {
            return;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        }
    }

    /**
     * 记录异常到当前 Span
     */
    public static void error(Throwable e) {
        if (tracer == null) {
            return;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.error(e);
        }
    }
}