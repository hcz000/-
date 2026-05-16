package com.example.demo.config;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket握手拦截器，用于在建立WebSocket连接前验证用户身份。
 * 该拦截器通过Sa-Token框架获取当前登录用户ID，并将其存入WebSocket会话属性中，
 * 若无法获取有效用户标识则拒绝握手请求。
 */
@Component
public class SaTokenHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * 存储用户ID的WebSocket会话属性键名
     */
    public static final String USER_ID_ATTR = "WEBSOCKET_USER_ID";

    /**
     * 在WebSocket握手之前执行，尝试从上下文或查询参数中提取用户登录ID。
     *
     * @param request   HTTP服务器请求对象
     * @param response  HTTP服务器响应对象
     * @param wsHandler WebSocket处理器
     * @param attributes WebSocket会话属性映射表，可用于传递数据到WebSocket会话中
     * @return 如果成功获取用户ID并设置到attributes中返回true，否则返回false以拒绝握手
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // 首先尝试从Sa-Token上下文中获取登录ID
        String loginId = getLoginIdFromContext();
        // 如果上下文中没有，则尝试从URL查询参数中解析token来获取登录ID
        if (!StringUtils.hasText(loginId)) {
            loginId = getLoginIdFromQuery(request.getURI().getQuery());
        }
        // 如果仍然没有有效的登录ID，则拒绝此次握手
        if (!StringUtils.hasText(loginId)) {
            return false;
        }
        // 将获取到的用户ID放入WebSocket会话属性中供后续使用
        attributes.put(USER_ID_ATTR, loginId);
        return true;
    }

    /**
     * 在WebSocket握手之后执行。
     *
     * @param request    HTTP服务器请求对象
     * @param response   HTTP服务器响应对象
     * @param wsHandler  WebSocket处理器
     * @param exception  握手过程中抛出的异常（如果有的话）
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // 当前实现无需额外处理
    }

    /**
     * 从Sa-Token安全上下文中获取当前登录用户的ID。
     *
     * @return 用户ID字符串，若未登录或发生异常则返回null
     */
    private String getLoginIdFromContext() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? null : loginId.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 从HTTP请求的查询参数中解析token字段，并据此获取对应的登录用户ID。
     *
     * @param query 查询字符串（如："token=abc123&other=value"）
     * @return 解析得到的用户ID字符串，若无有效token或解析失败则返回null
     */
    private String getLoginIdFromQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            String[] kv = part.split("=");
            // 查找名为"token"的查询参数
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
