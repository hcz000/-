package com.example.demo.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置优化
 * - 使用 Jackson2JsonRedisSerializer 替代默认序列化
 * - 优化 ObjectMapper 性能
 * - 支持 Java 8 时间类型
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 1. 使用 Jackson2JsonRedisSerializer 来序列化 Value
        Jackson2JsonRedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        
        // 2. 优化 ObjectMapper 配置
        ObjectMapper om = new ObjectMapper();
        // 可见性配置
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        
        // 性能优化: 忽略 null 值,减少传输数据量
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // 性能优化: 禁用不必要的特性
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 使用 ISO-8601 格式
        om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // 忽略未知属性
        
        // 支持 Java 8 时间类型 (LocalDateTime 等)
        om.registerModule(new JavaTimeModule());
        
        // 3. 使用 StringRedisSerializer 来序列化 Key
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key 采用 String 的序列化方式
        template.setKeySerializer(stringSerializer);
        // Hash 的 Key 也采用 String 的序列化方式
        template.setHashKeySerializer(stringSerializer);
        
        // Value 采用 JSON 的序列化方式
        template.setValueSerializer(jacksonSerializer);
        // Hash 的 Value 也采用 JSON 的序列化方式
        template.setHashValueSerializer(jacksonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}