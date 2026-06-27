package com.example.cloud.push.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程执行器（简化版）。
 * <p>
 * 单体版还做了 Micrometer 链路上下文透传，
 * 这里先用最简实现，足够 PushService 的 CompletableFuture 并行调用使用。
 */
@Configuration
public class VirtualThreadConfig {

    @Bean(name = "virtualThreadExecutor", destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("push-vt-", 0).factory());
    }
}
