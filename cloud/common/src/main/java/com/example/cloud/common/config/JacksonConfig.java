package com.example.cloud.common.config;

import com.example.cloud.common.util.LongToStringSerializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;

/**
 * Jackson 全局定制：
 * <ul>
 *   <li>所有 Long / long 字段序列化为字符串，防前端 JS BigInt 精度丢失（Snowflake 18 位 ID）</li>
 *   <li>Spring Data {@link PageImpl} 序列化字段重命名为前端约定格式</li>
 * </ul>
 *
 * 放在 common 模块，所有 svc 引用 common 自动生效。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, new LongToStringSerializer());
            builder.serializerByType(Long.TYPE, new LongToStringSerializer());
            builder.mixIn(PageImpl.class, PageMixIn.class);
        };
    }

    /**
     * Page 字段重命名：
     * <pre>
     *   content       → records
     *   totalElements → total
     *   number        → pageNum
     *   size          → pageSize
     * </pre>
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
