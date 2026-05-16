package com.example.demo.util;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 虚拟线程上下文传播工具类
 * <p>
 * 用于无法注入 virtualThreadExecutor 的场景
 * 正常情况推荐使用注入的 @Qualifier("virtualThreadExecutor") Executor
 */
public final class VirtualThreadTraceHelper {

    private VirtualThreadTraceHelper() {}

    /**
     * 包装 Runnable，使其在虚拟线程中执行时保持上下文
     */
    public static Runnable wrap(Runnable runnable, Tracer tracer) {
        Span currentSpan = tracer.currentSpan();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            var spanInScope = currentSpan != null ? tracer.withSpan(currentSpan) : null;
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }

            try {
                runnable.run();
            } finally {
                if (spanInScope != null) {
                    spanInScope.close();
                }
                MDC.clear();
            }
        };
    }

    /**
     * 包装 Supplier，使其在虚拟线程中执行时保持上下文
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier, Tracer tracer) {
        Span currentSpan = tracer.currentSpan();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            var spanInScope = currentSpan != null ? tracer.withSpan(currentSpan) : null;
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }

            try {
                return supplier.get();
            } finally {
                if (spanInScope != null) {
                    spanInScope.close();
                }
                MDC.clear();
            }
        };
    }
}