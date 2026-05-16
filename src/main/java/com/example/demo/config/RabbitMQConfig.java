package com.example.demo.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue notificationQueue() {
        return new Queue("notification", true);
    }

    @Bean
    public Queue likeSyncQueue() {
        return new Queue("like-sync", true);
    }

    @Bean
    public Queue likeUserSyncQueue() {
        return new Queue("like-user-sync", true);
    }

    @Bean
    public Queue primaryCommentDeleteQueue() {
        return new Queue("primary-comment-delete", true);
    }
}