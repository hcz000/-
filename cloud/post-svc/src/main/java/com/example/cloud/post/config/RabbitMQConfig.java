package com.example.cloud.post.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑（post-svc 负责声明 exchange + 自己关心的 queue）。
 * <p>
 * <ul>
 *   <li>Exchange: {@code post.events.exchange}（topic 类型）</li>
 *   <li>Queue:    {@code push.post-created.queue}（push-svc 消费）</li>
 *   <li>Binding:  routing key = {@code post.created}</li>
 * </ul>
 * <p>
 * 由 post-svc 声明 push-svc 的 queue，是「Producer Owns Topology」做法——
 * 即使 push-svc 还没启动，MQ 拓扑也已经就绪，消息可以先堆积在 queue 里。
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "post.events.exchange";
    public static final String PUSH_QUEUE = "push.post-created.queue";
    public static final String POST_CREATED_KEY = "post.created";

    @Bean
    public TopicExchange postEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue pushPostCreatedQueue() {
        return QueueBuilder.durable(PUSH_QUEUE).build();
    }

    @Bean
    public Binding pushPostCreatedBinding(TopicExchange postEventsExchange, Queue pushPostCreatedQueue) {
        return BindingBuilder.bind(pushPostCreatedQueue).to(postEventsExchange).with(POST_CREATED_KEY);
    }

    /** 使用 Jackson 序列化消息体（默认是 SimpleMessageConverter，只能传 byte[] / String）。*/
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
