package com.example.demo.config;

import com.example.demo.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;
    @Resource
    private SaTokenHandshakeInterceptor saTokenHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 WebSocket 处理器，处理 /ws/chat 和 /ws 路径
        registry.addHandler(chatWebSocketHandler, "/ws/chat", "/ws")
                .addInterceptors(saTokenHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}