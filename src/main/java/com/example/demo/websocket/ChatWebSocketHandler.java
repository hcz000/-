package com.example.demo.websocket;

import com.example.demo.config.SaTokenHandshakeInterceptor;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.dto.ChatMessageResponse;
import com.example.demo.entity.dto.ChatMessageSendRequest;
import com.example.demo.entity.dto.NotificationResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.ChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final int WS_SEND_TIME_LIMIT_MS = 10_000;
    private static final int WS_SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ChatService chatService;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private Executor virtualThreadExecutor;

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessionIndex = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userKey = userKey(session);
        if (!StringUtils.hasText(userKey)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        WebSocketSession safeSession = safeSession(session);
        sessionIndex.put(session.getId(), safeSession);
        sessions.computeIfAbsent(userKey, key -> ConcurrentHashMap.newKeySet()).add(safeSession);
        send(safeSession, infoResponse("连接成功"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = userId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        virtualThreadExecutor.execute(() -> handleIncomingMessage(session, message, userId));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeSession(session);
    }

    public void broadcastNotification(Long userId, NotificationResponse response) {
        if (userId == null) {
            return;
        }
        virtualThreadExecutor.execute(() -> broadcast(String.valueOf(userId), notificationResponse(response)));
    }

    private void handleIncomingMessage(WebSocketSession session, TextMessage message, Long userId) {
        WebSocketSession managedSession = managedSession(session);
        try {
            ChatMessageSendRequest payload = objectMapper.readValue(message.getPayload(), ChatMessageSendRequest.class);
            ChatMessage saved = chatService.sendMessage(userId, payload.getFriendId(), payload.getContent());
            Map<String, Object> response = chatResponse(ChatMessageResponse.from(saved));
            broadcast(String.valueOf(userId), response);
            broadcast(String.valueOf(payload.getFriendId()), response);
        } catch (BusinessException e) {
            send(managedSession, errorResponse(e.getMessage()));
        } catch (IOException e) {
            send(managedSession, errorResponse("消息解析失败"));
        } catch (Exception e) {
            send(managedSession, errorResponse("发送失败"));
        }
    }

    private void broadcast(String userId, Map<String, Object> response) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = toText(response);
        for (WebSocketSession webSocketSession : userSessions) {
            send(webSocketSession, textMessage);
        }
    }

    private void send(WebSocketSession session, Map<String, Object> response) {
        send(session, toText(response));
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException ignored) {
        }
    }

    private void removeSession(WebSocketSession session) {
        String userKey = userKey(session);
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        WebSocketSession managedSession = sessionIndex.remove(session.getId());
        if (managedSession == null) {
            managedSession = session;
        }
        Set<WebSocketSession> userSessions = sessions.get(userKey);
        if (userSessions == null) {
            return;
        }
        userSessions.remove(managedSession);
        if (userSessions.isEmpty()) {
            sessions.remove(userKey);
        }
    }

    private Long userId(WebSocketSession session) {
        String key = userKey(session);
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            return Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String userKey(WebSocketSession session) {
        Object value = session.getAttributes().get(SaTokenHandshakeInterceptor.USER_ID_ATTR);
        return value == null ? null : value.toString();
    }

    private WebSocketSession safeSession(WebSocketSession session) {
        if (session instanceof ConcurrentWebSocketSessionDecorator) {
            return session;
        }
        return new ConcurrentWebSocketSessionDecorator(
                session,
                WS_SEND_TIME_LIMIT_MS,
                WS_SEND_BUFFER_LIMIT_BYTES);
    }

    private WebSocketSession managedSession(WebSocketSession session) {
        return sessionIndex.getOrDefault(session.getId(), session);
    }

    private TextMessage toText(Map<String, Object> response) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("无法序列化消息", e);
        }
    }

    private Map<String, Object> chatResponse(ChatMessageResponse response) {
        return wsResponse("CHAT", response);
    }

    private Map<String, Object> errorResponse(String message) {
        return wsResponse("ERROR", message);
    }

    private Map<String, Object> infoResponse(String message) {
        return wsResponse("INFO", message);
    }

    private Map<String, Object> notificationResponse(NotificationResponse response) {
        return wsResponse("NOTIFICATION", response);
    }

    private Map<String, Object> wsResponse(String type, Object data) {
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", type);
        payload.put("data", data);
        return payload;
    }
}
