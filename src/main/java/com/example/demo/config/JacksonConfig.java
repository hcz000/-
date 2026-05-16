package com.example.demo.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // 全局 Long -> String，避免前端 JS 精度丢失（Snowflake 18位ID）
            builder.serializerByType(Long.class, new LongToStringSerializer());
            builder.serializerByType(Long.TYPE, new LongToStringSerializer());
            // 将 Spring Data JPA PageImpl 的 content 字段序列化为 records，与前端期望一致
            builder.mixIn(PageImpl.class, PageMixIn.class);
        };
    }

    /**
     * Page 混入类：重命名字段以匹配前端期望格式
     * - content -> records
     * - totalElements -> total
     * - number -> pageNum
     * - size -> pageSize
     */
    @JsonPropertyOrder({"records", "total", "pageNum", "pageSize", "pages", "first", "last", "empty"})
    public static abstract class PageMixIn {
        @JsonProperty("records")
        private java.util.List<?> content;

        @JsonProperty("total")
        private long totalElements;

        @JsonProperty("pages")
        private int totalPages;

        @JsonProperty("pageNum")
        private int number;

        @JsonProperty("pageSize")
        private int size;
    }
}
