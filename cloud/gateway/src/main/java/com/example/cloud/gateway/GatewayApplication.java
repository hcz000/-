package com.example.cloud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关启动类
 * <p>
 * 端口：8080（外部唯一入口）
 * 后续职责：
 * <ul>
 *   <li>路由分发到 user-svc / post-svc / push-svc</li>
 *   <li>统一 sa-token 鉴权（自定义 GlobalFilter）</li>
 *   <li>Redis + 令牌桶限流</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
