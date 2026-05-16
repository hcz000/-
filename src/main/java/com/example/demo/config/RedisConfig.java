package com.example.demo.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 1. 使用 Jackson2JsonRedisSerializer 来序列化 Value
        Jackson2JsonRedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        
        // 解决查询缓存转换异常的问题
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        // jacksonSerializer.setObjectMapper(om); // 旧版本写法
        // 新版本通常直接在构造时传入或通过其他方式配置

        // 2. 使用 StringRedisSerializer 来序列化 Key
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