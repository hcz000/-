package com.example.cloud.user.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 调用时把 Seata 全局事务 xid 注入 HTTP header，让下游服务能加入同一个全局事务。
 * <p>
 * 单独用 {@code io.seata:seata-spring-boot-starter} 不会自动拦截 OpenFeign，
 * 必须手动加 RequestInterceptor。Spring Cloud Alibaba 的 seata starter 自带这个能力，
 * 但当前用纯 seata 客户端，所以这里补上。
 */
@Slf4j
@Configuration
public class FeignSeataConfig {

    public static final String XID_HEADER = RootContext.KEY_XID;  // "TX_XID"

    @Bean
    public RequestInterceptor seataFeignRequestInterceptor() {
        return (RequestTemplate template) -> {
            String xid = RootContext.getXID();
            if (xid != null && !xid.isBlank()) {
                template.header(XID_HEADER, xid);
                log.debug("[feign-seata] inject xid={} into header {}", xid, XID_HEADER);
            }
        };
    }
}
