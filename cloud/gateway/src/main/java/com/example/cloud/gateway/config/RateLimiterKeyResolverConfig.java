package com.example.cloud.gateway.config;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 网关限流的 Key 提取策略。
 * <p>
 * Spring Cloud Gateway 的 {@code RequestRateLimiter} 需要一个 {@link KeyResolver} 来决定
 * 「按什么维度限流」。三个常见选择：
 * <ol>
 *   <li>按 userId（已登录时）—— 同一个用户全局共享配额</li>
 *   <li>按 IP —— 兜底，未登录请求也能限</li>
 *   <li>按 path —— 整条路由的总流量上限</li>
 * </ol>
 *
 * 这里组合策略：登录用户按 userId 限，未登录按 IP 限。
 */
@Slf4j
@Configuration
public class RateLimiterKeyResolverConfig {

    /**
     * 默认 key 解析器：登录用户用 userId，未登录用 IP。
     * <p>
     * 在 application.yml 的 RequestRateLimiter filter 里用 SpEL 引用：
     * {@code key-resolver: "#{@userOrIpKeyResolver}"}
     */
    @Primary
    @Bean("userOrIpKeyResolver")
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            if (StpUtil.isLogin()) {
                String userId = StpUtil.getLoginIdAsString();
                log.trace("[rate-limit] key=user:{}", userId);
                return Mono.just("user:" + userId);
            }
            // 取真实 IP（考虑 X-Forwarded-For，但简化处理）
            String ip = exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            log.trace("[rate-limit] key=ip:{}", ip);
            return Mono.just("ip:" + ip);
        };
    }

    /**
     * 单独按 IP 限流的 key 解析器（用于 /api/user/Login 这种不需要 token 的接口，
     * 防止有人无限调用登录接口暴破密码）。
     */
    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just("ip:" + ip);
        };
    }
}
