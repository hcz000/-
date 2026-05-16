package com.example.demo.config;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路追踪过滤器
 * <p>
 * 为每个 HTTP 请求自动生成 TraceId，并放入 MDC 供日志使用
 */
@Component
public class TraceFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public TraceFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = null;
        String spanId = null;

        // 获取当前 traceId
        var currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            var traceContext = currentSpan.context();
            traceId = traceContext.traceId();
            spanId = traceContext.spanId();
        }

        // 放入 MDC，供日志输出
        if (traceId != null) {
            MDC.put("traceId", traceId);
            MDC.put("spanId", spanId);

            // 响应头返回 traceId，方便排查问题
            response.setHeader("X-Trace-Id", traceId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理 MDC
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}