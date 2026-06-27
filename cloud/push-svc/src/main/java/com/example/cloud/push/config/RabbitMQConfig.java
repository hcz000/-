package com.example.cloud.push.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * push-svc 消费者侧 RabbitMQ 配置。
 * <p>
 * Queue 的声明也写在这里（幂等），即使 post-svc 没启动，push-svc 起来也能确保 queue 存在。
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String PUSH_POST_CREATED_QUEUE = "push.post-created.queue";

    @Bean
    public Queue pushPostCreatedQueue() {
        return QueueBuilder.durable(PUSH_POST_CREATED_QUEUE).build();
    }

    /** 跨服务消息用 JSON，和 post-svc 保持一致 */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
