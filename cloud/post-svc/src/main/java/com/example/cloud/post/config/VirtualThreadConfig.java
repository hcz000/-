package com.example.cloud.post.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 虚拟线程执行器。
 * <p>
 * 提供两个 bean：
 * <ul>
 *   <li>{@code virtualThreadExecutor} —— 普通异步任务用</li>
 *   <li>{@code virtualThreadSchedulerExecutor} —— LikeBufferTrigger / ReplyCountBufferTrigger 周期性任务用</li>
 * </ul>
 */
@Configuration
public class VirtualThreadConfig {

    @Bean(name = "virtualThreadExecutor", destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("post-vt-", 0).factory());
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService virtualThreadSchedulerExecutor() {
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        return Executors.newScheduledThreadPool(
                poolSize,
                Thread.ofVirtual().name("post-sched-vt-", 0).factory());
    }
}
