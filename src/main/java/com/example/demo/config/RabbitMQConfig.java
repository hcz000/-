package com.example.demo.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 完整配置
 * 包含：交换机、队列、死信队列、消息确认、重试机制
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 交换机定义 ====================
    
    /**
     * 主交换机（Direct类型）
     */
    @Bean
    public DirectExchange mainExchange() {
        return ExchangeBuilder.directExchange("main.exchange")
                .durable(true)
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange("dlx.exchange")
                .durable(true)
                .build();
    }

    // ==================== 死信队列 ====================
    
    /**
     * 死信队列 - 用于处理失败的消息
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("dead-letter.queue")
                .build();
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dlx.routingkey");
    }

    // ==================== 业务队列配置 ====================
    
    /**
     * 通知队列（带死信配置和重试策略）
     */
    @Bean
    public Queue notificationQueue() {
        Map<String, Object> args = new HashMap<>();
        // 死信交换机
        args.put("x-dead-letter-exchange", "dlx.exchange");
        args.put("x-dead-letter-routing-key", "dlx.routingkey");
        // 消息TTL（毫秒）- 消息在队列中的最大存活时间
        args.put("x-message-ttl", 86400000); // 24小时
        // 队列最大长度
        args.put("x-max-length", 100000);
        
        return QueueBuilder.durable("notification")
                .withArguments(args)
                .build();
    }

    /**
     * 点赞同步队列
     */
    @Bean
    public Queue likeSyncQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "dlx.exchange");
        args.put("x-dead-letter-routing-key", "dlx.routingkey");
        args.put("x-message-ttl", 3600000); // 1小时
        
        return QueueBuilder.durable("like-sync")
                .withArguments(args)
                .build();
    }

    /**
     * 点赞用户同步队列
     */
    @Bean
    public Queue likeUserSyncQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "dlx.exchange");
        args.put("x-dead-letter-routing-key", "dlx.routingkey");
        args.put("x-message-ttl", 3600000); // 1小时
        
        return QueueBuilder.durable("like-user-sync")
                .withArguments(args)
                .build();
    }

    /**
     * 一级评论删除队列：已废弃
     * <p>
     * 单 JVM 内的级联清理改用 Spring 事件 PrimaryCommentDeletedEvent，
     * 配合 @TransactionalEventListener(AFTER_COMMIT) 实现"主事务提交后才清理二级评论"。
     */

    // ==================== 绑定队列到交换机 ====================
    
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(mainExchange())
                .with("notification.routingkey");
    }

    @Bean
    public Binding likeSyncBinding() {
        return BindingBuilder.bind(likeSyncQueue())
                .to(mainExchange())
                .with("like-sync.routingkey");
    }

    @Bean
    public Binding likeUserSyncBinding() {
        return BindingBuilder.bind(likeUserSyncQueue())
                .to(mainExchange())
                .with("like-user-sync.routingkey");
    }

    // ==================== 消息转换器 ====================
    
    /**
     * JSON消息转换器（替代默认的Java序列化）
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置RabbitTemplate（支持消息确认）
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        
        // 开启发布确认
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                // 消息成功到达交换机
            } else {
                // 消息未到达交换机，记录日志或重试
                org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                    .error("消息发送失败: {}", cause);
            }
        });
        
        // 开启返回回调（消息未路由到队列时触发）
        template.setReturnsCallback(returned -> {
            org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                .error("消息未路由到队列: {}", returned.getMessage());
        });
        
        return template;
    }
}