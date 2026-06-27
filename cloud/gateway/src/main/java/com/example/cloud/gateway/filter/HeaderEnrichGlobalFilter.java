package com.example.cloud.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 鉴权通过后，把 userId 注入到 {@code X-User-Id} 请求头，
 * 透传给下游服务（user-svc / post-svc / push-svc）。
 * <p>
 * 这样下游就不必再走一遍 sa-token 校验 Redis 会话，可以直接信任 header，
 * 也方便统一的「内部鉴权下沉」演进。
 * <p>
 * 注意：客户端如果自己往请求里塞 X-User-Id 是无效的——
 * 这里用 {@code .header(name, values)} 是 set 语义，会覆盖客户端伪造的值。
 */
@Slf4j
@Component
public class HeaderEnrichGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        // 无论是否登录，都先把客户端可能伪造的 X-User-Id 清掉
        builder.headers(h -> h.remove(HEADER_USER_ID));

        if (StpUtil.isLogin()) {
            String userId = StpUtil.getLoginIdAsString();
            builder.header(HEADER_USER_ID, userId);
            log.debug("[gateway-header] inject X-User-Id={}", userId);
        }
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    /**
     * 比 sa-token 的 SaReactorFilter 晚执行（SaReactorFilter 是 WebFilter，
     * 这里是 GlobalFilter，已经天然晚了）。返回较小的 order 让它在网关 filter 链里靠前。
     */
    @Override
    public int getOrder() {
        return -1;
    }
}
