package com.example.cloud.gateway.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 网关层 sa-token 鉴权配置（WebFlux Reactor 模式）。
 * <p>
 * 鉴权策略：
 * <ul>
 *   <li>白名单路径直接放行：登录 / 注册 / 验证码 / 健康检查 / 探针 / 静态资源</li>
 *   <li>其他所有 {@code /api/**} 必须携带合法 token，否则返回 401</li>
 * </ul>
 *
 * 鉴权通过后，会话信息存在 sa-token 上下文里，
 * 由 {@link com.example.cloud.gateway.filter.HeaderEnrichGlobalFilter} 把
 * userId 透传到下游服务的 {@code X-User-Id} header。
 */
@Slf4j
@Configuration
public class SaTokenReactorConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 白名单：以下路径无需登录即可访问。
     */
    public static final String[] WHITE_LIST = {
            // user-svc 登录前接口
            "/api/user/Login",
            "/api/user/registered",
            "/api/user/send",
            // user-svc 公开查询
            "/api/user/search",
            "/api/user/info/**",
            "/api/user/profile",        // 未登录也能调，业务返回 data=null
            // post-svc / push-svc 公开接口
            "/api/post/health",
            "/api/push/random",
            "/api/push/hot",
            "/api/push/_ping/**",
            "/api/push/_strategy",
            // 平台基础设施
            "/actuator/**",
            "/favicon.ico"
    };

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .setAuth(obj -> {
                    SaRouter.match("/**")
                            .notMatch(WHITE_LIST)
                            .check(r -> {
                                StpUtil.checkLogin();
                                log.debug("[gateway-auth] pass userId={}", StpUtil.getLoginIdAsString());
                            });
                })
                .setError(this::buildAuthErrorJson);
    }

    /**
     * 鉴权失败时返回的 JSON 字符串（sa-token 的 setError 直接把返回值当 body 写出去，
     * Map 对象会被 toString，所以这里必须手动序列化为 JSON）。
     */
    private String buildAuthErrorJson(Throwable e) {
        Map<String, Object> body = new HashMap<>(3);
        if (e instanceof NotLoginException) {
            body.put("code", 401);
            body.put("msg", "用户未登录或 token 已失效");
            body.put("data", null);
        } else {
            body.put("code", 500);
            body.put("msg", "鉴权失败：" + e.getMessage());
            body.put("data", null);
            log.warn("[gateway-auth] unexpected error", e);
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException jsonEx) {
            return "{\"code\":500,\"msg\":\"auth error\",\"data\":null}";
        }
    }
}
