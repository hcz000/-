package com.example.cloud.user.config;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：在建立连接前用 sa-token 验证身份，
 * 把 userId 写入 WebSocket session 的 attributes，供 ChatWebSocketHandler 使用。
 * <p>
 * 支持两种取 token 的方式：
 * <ol>
 *   <li>sa-token 上下文（同源 HTTP 请求里已有 Authorization 头）</li>
 *   <li>URL 查询参数 ?token=xxx（前端 WebSocket 连接通常这么传）</li>
 * </ol>
 */
@Component
public class SaTokenHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTR = "WEBSOCKET_USER_ID";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String loginId = getLoginIdFromContext();
        if (!StringUtils.hasText(loginId)) {
            loginId = getLoginIdFromQuery(request.getURI().getQuery());
        }
        if (!StringUtils.hasText(loginId)) return false;
        attributes.put(USER_ID_ATTR, loginId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String getLoginIdFromContext() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? null : loginId.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getLoginIdFromQuery(String query) {
        if (!StringUtils.hasText(query)) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=");
            if (kv.length == 2 && "token".equals(kv[0])) {
                String token = kv[1];
                if (StringUtils.hasText(token)) {
                    try {
                        Object loginId = StpUtil.getLoginIdByToken(token);
                        return loginId == null ? null : loginId.toString();
                    } catch (Exception ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
