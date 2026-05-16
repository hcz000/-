package com.example.demo.config;

import brave.propagation.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 虚拟线程全局上下文传播配置
 * <p>
 * 解决虚拟线程中 ThreadLocal（TraceContext、MDC）上下文丢失问题
 * 所有虚拟线程自动传播链路追踪上下文
 */
@Configuration
public class VirtualThreadContextConfig {

    /**
     * 全局 TaskDecorator
     * Spring 会自动应用到 @Async、@Scheduled、RabbitMQ 等
     */
    @Bean
    public TaskDecorator taskDecorator(CurrentTraceContext currentTraceContext) {
        return new ContextPropagatingTaskDecorator();
    }

    /**
     * 带上下文传播的虚拟线程执行器
     * 用于 CompletableFuture.supplyAsync 等
     */
    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor(Tracer tracer) {
        Executor delegate = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        return new ContextPropagatingExecutor(delegate, tracer);
    }

    /**
     * 自定义上下文传播执行器
     */
    public static class ContextPropagatingExecutor implements Executor {
        private final Executor delegate;
        private final Tracer tracer;

        public ContextPropagatingExecutor(Executor delegate, Tracer tracer) {
            this.delegate = delegate;
            this.tracer = tracer;
        }

        @Override
        public void execute(Runnable command) {
            // 捕获当前上下文
            var currentSpan = tracer.currentSpan();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();

            delegate.execute(() -> {
                // 在新线程中恢复上下文
                var spanInScope = currentSpan != null ? tracer.withSpan(currentSpan) : null;
                if (mdcContext != null) {
                    MDC.setContextMap(mdcContext);
                }

                try {
                    command.run();
                } finally {
                    if (spanInScope != null) {
                        spanInScope.close();
                    }
                    MDC.clear();
                }
            });
        }
    }
}