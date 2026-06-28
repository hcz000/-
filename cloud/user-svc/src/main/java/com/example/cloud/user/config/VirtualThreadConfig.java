package com.example.cloud.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程执行器，供 ChatWebSocketHandler 等高并发场景使用。
 */
@Configuration
public class VirtualThreadConfig {

    @Bean(name = "virtualThreadExecutor", destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("user-vt-", 0).factory());
    }
}
