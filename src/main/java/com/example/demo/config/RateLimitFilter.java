package com.example.demo.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 用户级限流过滤器
 * <p>
 * 分级限流策略：
 * - 未登录用户：IP 限流（5 QPS）
 * - 已登录用户：UserID 限流（20 QPS）
 * <p>
 * 使用 Caffeine 缓存管理 Bucket，自动过期清理
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> bucketCache;

    /**
     * 未登录用户限流阈值（QPS）
     */
    private final int anonymousPermitsPerSecond;

    /**
     * 已登录用户限流阈值（QPS）
     */
    private final int authenticatedPermitsPerSecond;

    /**
     * 等待获取令牌的超时时间（毫秒），默认 0 表示不等待直接返回 429
     */
    private final long acquireTimeoutMillis;
    private final String tokenHeaderName;
    private final boolean enabled;

    public RateLimitFilter(
            @org.springframework.beans.factory.annotation.Value("${rate-limit.enabled:true}") boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${rate-limit.anonymous-permits:5}") int anonymousPermitsPerSecond,
            @org.springframework.beans.factory.annotation.Value("${rate-limit.authenticated-permits:20}") int authenticatedPermitsPerSecond,
            @org.springframework.beans.factory.annotation.Value("${rate-limit.acquire-timeout-millis:0}") long acquireTimeoutMillis,
            @org.springframework.beans.factory.annotation.Value("${sa-token.token-name:Authorization}") String tokenHeaderName,
            @org.springframework.beans.factory.annotation.Value("${rate-limit.cache-expire-minutes:30}") long cacheExpireMinutes,
            @org.springframework.beans.factory.annotation.Value("${rate-limit.max-cache-size:10000}") int maxCacheSize) {
        this.anonymousPermitsPerSecond = Math.max(1, anonymousPermitsPerSecond);
        this.authenticatedPermitsPerSecond = Math.max(1, authenticatedPermitsPerSecond);
        this.acquireTimeoutMillis = Math.max(0, acquireTimeoutMillis);
        this.tokenHeaderName = StringUtils.hasText(tokenHeaderName) ? tokenHeaderName : "Authorization";
        this.enabled = enabled;

        // 使用 Caffeine 构建 Cache，自动过期清理
        // Native Image兼容：使用expireAfterWrite替代expireAfterAccess
        this.bucketCache = Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterWrite(Duration.ofMinutes(cacheExpireMinutes))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 限流关闭时直接放行
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取限流 Key 和对应的限流阈值
        RateLimitContext context = getRateLimitContext(request);
        Bucket bucket = bucketCache.get(context.key(), k -> createBucket(context.permitsPerSecond()));

        boolean allowed;
        long remainingTokens = 0;
        long waitTimeNanos = 0;

        // 先尝试非阻塞消费，获取剩余令牌信息
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe != null && probe.isConsumed()) {
            allowed = true;
            remainingTokens = Math.max(0, probe.getRemainingTokens());
        } else if (acquireTimeoutMillis > 0) {
            // 允许等待时，尝试阻塞消费
            try {
                allowed = bucket.asBlocking().tryConsume(1, Duration.ofMillis(acquireTimeoutMillis));
                // 阻塞模式下重新获取剩余令牌
                remainingTokens = Math.max(0, bucket.getAvailableTokens());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writeError(response, "请求被中断，请稍后再试");
                return;
            }
        } else {
            allowed = false;
            if (probe != null) {
                waitTimeNanos = probe.getNanosToWaitForRefill();
            }
        }

        // 设置 RateLimit 响应头
        setRateLimitHeaders(response, context.permitsPerSecond(), remainingTokens, waitTimeNanos);

        if (!allowed) {
            writeError(response, "请求过于频繁，请稍后再试");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 设置限流响应头
     *
     * @param response           HTTP 响应
     * @param limit              限流阈值
     * @param remaining          剩余令牌数
     * @param nanosToRefill      等待填充时间（纳秒）
     */
    private void setRateLimitHeaders(HttpServletResponse response, int limit, long remaining, long nanosToRefill) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        // 转换为秒数（向上取整）
        long resetSeconds = (nanosToRefill + 999_999_999L) / 1_000_000_000L;
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));
    }

    /**
     * 获取限流上下文（包含 key 和对应的限流阈值）
     */
    private RateLimitContext getRateLimitContext(HttpServletRequest request) {
        // 已登录用户：使用 UserID 限流，阈值较高
        String token = extractToken(request);
        // 2. 用无上下文 API 验证 (直接查 Redis/内存，不碰 ThreadLocal)
        Object loginId = StpUtil.getLoginIdByToken(token);

        if (loginId != null) {
            return new RateLimitContext("user:" + loginId.toString(), authenticatedPermitsPerSecond);
        }
        // 未登录用户：使用 IP 限流，阈值较低
        String ip = getClientIp(request);
        return new RateLimitContext("ip:" + ip, anonymousPermitsPerSecond);
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader(tokenHeaderName);
        if (!StringUtils.hasText(token) && !"Authorization".equalsIgnoreCase(tokenHeaderName)) {
            token = request.getHeader("Authorization");
        }
        if (!StringUtils.hasText(token) && !"satoken".equalsIgnoreCase(tokenHeaderName)) {
            token = request.getHeader("satoken");
        }
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return token.substring(7).trim();
        }
        return token.trim();
    }

    /**
     * 为每个用户创建独立的令牌桶
     *
     * @param permitsPerSecond 每秒允许的请求数
     */
    private Bucket createBucket(int permitsPerSecond) {
        Bandwidth bandwidth = Bandwidth.classic(permitsPerSecond,
                Refill.greedy(permitsPerSecond, Duration.ofSeconds(1)));
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(SaResult.error(message).toString());
    }

    /**
     * 限流上下文记录
     *
     * @param key             限流 Key（user:xxx 或 ip:xxx）
     * @param permitsPerSecond 每秒允许的请求数
     */
    private record RateLimitContext(String key, int permitsPerSecond) {
    }
}
