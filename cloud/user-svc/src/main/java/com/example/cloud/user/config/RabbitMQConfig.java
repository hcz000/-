package com.example.cloud.user.config;

import com.example.cloud.common.api.notification.NotificationEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user-svc 的 RabbitMQ 拓扑（通知模块）。
 * <p>
 * <ul>
 *   <li>Exchange (topic): {@code notification.events} —— 所有服务发通知都往这里发</li>
 *   <li>Queue:            {@code notification.user-svc.queue}</li>
 *   <li>Binding:          {@code notification.#}（接所有通知子主题）</li>
 * </ul>
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NotificationEvent.EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NotificationEvent.QUEUE).build();
    }

    @Bean
    public Binding notificationBinding(TopicExchange notificationExchange, Queue notificationQueue) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(NotificationEvent.ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}
