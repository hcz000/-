package com.example.demo.config;

import brave.propagation.CurrentTraceContext;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextScheduledExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.contextpropagation.ObservationAwareBaggageThreadLocalAccessor;
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class VirtualThreadContextConfig {

    @Bean
    public TaskDecorator taskDecorator(CurrentTraceContext currentTraceContext, ContextSnapshotFactory contextSnapshotFactory) {
        return new ContextPropagatingTaskDecorator(contextSnapshotFactory);
    }

    @Bean
    public ContextSnapshotFactory contextSnapshotFactory(ContextRegistry contextRegistry) {
        return ContextSnapshotFactory.builder().contextRegistry(contextRegistry).build();
    }

    @Bean
    public ContextRegistry contextRegistry(ObservationRegistry observationRegistry, Tracer tracer) {
        ContextRegistry registry = new ContextRegistry();
        registry.registerThreadLocalAccessor(new ObservationThreadLocalAccessor(observationRegistry));
        registry.registerThreadLocalAccessor(new ObservationAwareSpanThreadLocalAccessor(observationRegistry, tracer));
        registry.registerThreadLocalAccessor(new ObservationAwareBaggageThreadLocalAccessor(observationRegistry, tracer));
        registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor());
        return registry;
    }

    @Bean(name = "virtualThreadExecutor", destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor(ContextSnapshotFactory contextSnapshotFactory) {
        ExecutorService delegate = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("app-vt-", 0).factory());
        return ContextExecutorService.wrap(delegate, contextSnapshotFactory);
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService virtualThreadSchedulerExecutor(ContextSnapshotFactory contextSnapshotFactory) {
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        ScheduledExecutorService delegate = Executors.newScheduledThreadPool(
                poolSize,
                Thread.ofVirtual().name("sched-vt-", 0).factory());
        return ContextScheduledExecutorService.wrap(delegate, contextSnapshotFactory);
    }
}
