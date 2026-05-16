package com.example.demo.config;

import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 链路追踪配置
 * <p>
 * 基于 Micrometer Tracing + Brave + Zipkin
 */
@Configuration
public class TraceConfig {

    /**
     * 采样率配置
     * ALWAYS_SAMPLE - 100% 采样
     * NEVER_SAMPLE - 0% 采样
     * 自定义采样率可以使用 Sampler.create(0.1f) 表示 10% 采样
     */
    @Bean
    public Sampler defaultSampler() {
        // 开发环境 100% 采样，生产环境建议降低
        return Sampler.ALWAYS_SAMPLE;
    }

    /**
     * 配置 TraceContext，支持 MDC 日志输出 traceId
     */
    @Bean
    public ThreadLocalCurrentTraceContext threadLocalCurrentTraceContext() {
        return ThreadLocalCurrentTraceContext.newBuilder()
                .addScopeDecorator(MDCScopeDecorator.get()) // 支持 MDC 日志输出
                .build();
    }
}