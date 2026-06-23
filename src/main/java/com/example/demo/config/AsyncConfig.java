package com.example.demo.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 异步与定时任务配置，与 Bean 定义解耦
 * <p>
 * 将 @EnableAsync / AsyncConfigurer / SchedulingConfigurer
 * 从 VirtualThreadContextConfig 中分离出来，避免循环依赖
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer, SchedulingConfigurer {

    @Resource(name = "virtualThreadExecutor")
    private ExecutorService virtualThreadExecutor;

    @Resource
    private ScheduledExecutorService virtualThreadSchedulerExecutor;

    @Override
    public Executor getAsyncExecutor() {
        return virtualThreadExecutor;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(virtualThreadSchedulerExecutor);
    }
}
