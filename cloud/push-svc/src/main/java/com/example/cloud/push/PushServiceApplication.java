package com.example.cloud.push;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 推送服务启动类
 * <p>
 * 端口：8083
 * 职责：推荐策略 / 候选池 / 已曝光去重 / 用户兴趣向量 buffer 刷盘
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.example.cloud")
@EnableScheduling
public class PushServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PushServiceApplication.class, args);
    }
}
