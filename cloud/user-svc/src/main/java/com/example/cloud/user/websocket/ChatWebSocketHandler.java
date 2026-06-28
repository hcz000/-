package com.example.cloud.user.websocket;

import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.user.config.SaTokenHandshakeInterceptor;
import com.example.cloud.user.dto.ChatMessageResponse;
import com.example.cloud.user.dto.ChatMessageSendRequest;
import com.example.cloud.user.dto.NotificationResponse;
import com.example.cloud.user.entity.ChatMessage;
import com.example.cloud.user.service.ChatService;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * WebSocket 聊天处理器。
 * <p>
 * 双重职责：
 * <ol>
 *   <li>处理 /ws/chat（前端推消息上来 → 落库 → 给双方在线 session 推送）</li>
 *   <li>{@link #broadcastNotification(Long, NotificationResponse)} 给指定用户的所有在线 session 推送通知，
 *       由 NotificationEventListener 在消费 MQ 通知事件后调用</li>
 * </ol>
 */
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

    /** userKey → 该用户所有在线 session（一个用户可能多端登录） */
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    /** sessionId → session 的反向索引，移除时复杂度从 O(n) 降到 O(1) */
    private final Map<String, WebSocketSession> sessionIndex = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userKey = userKey(session);
        if (!StringUtils.hasText(userKey)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        WebSocketSession safe = safeSession(session);
        sessionIndex.put(session.getId(), safe);
        sessions.computeIfAbsent(userKey, k -> ConcurrentHashMap.newKeySet()).add(safe);
        send(safe, infoResponse("连接成功"));
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

    /**
     * 给指定 userId 的所有在线 session 推送通知。
     * 由 NotificationEventListener 在 MQ 消费后调用。
     */
    public void broadcastNotification(Long userId, NotificationResponse response) {
        if (userId == null) return;
        virtualThreadExecutor.execute(() -> broadcast(String.valueOf(userId), notificationResponse(response)));
    }

    private void handleIncomingMessage(WebSocketSession session, TextMessage message, Long userId) {
        WebSocketSession managed = managedSession(session);
        try {
            ChatMessageSendRequest payload = objectMapper.readValue(message.getPayload(), ChatMessageSendRequest.class);
            ChatMessage saved = chatService.sendMessage(userId, payload.getFriendId(), payload.getContent());
            Map<String, Object> resp = chatResponse(ChatMessageResponse.from(saved));
            broadcast(String.valueOf(userId), resp);
            broadcast(String.valueOf(payload.getFriendId()), resp);
        } catch (BusinessException e) {
            send(managed, errorResponse(e.getMessage()));
        } catch (IOException e) {
            send(managed, errorResponse("消息解析失败"));
        } catch (Exception e) {
            send(managed, errorResponse("发送失败"));
        }
    }

    private void broadcast(String userId, Map<String, Object> response) {
        if (!StringUtils.hasText(userId)) return;
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) return;
        TextMessage text = toText(response);
        for (WebSocketSession s : userSessions) send(s, text);
    }

    private void send(WebSocketSession session, Map<String, Object> response) {
        send(session, toText(response));
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(message);
        } catch (IOException ignored) {
        }
    }

    private void removeSession(WebSocketSession session) {
        String userKey = userKey(session);
        if (!StringUtils.hasText(userKey)) return;
        WebSocketSession managed = sessionIndex.remove(session.getId());
        if (managed == null) managed = session;
        Set<WebSocketSession> userSessions = sessions.get(userKey);
        if (userSessions == null) return;
        userSessions.remove(managed);
        if (userSessions.isEmpty()) sessions.remove(userKey);
    }

    private Long userId(WebSocketSession session) {
        String key = userKey(session);
        if (!StringUtils.hasText(key)) return null;
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
        if (session instanceof ConcurrentWebSocketSessionDecorator) return session;
        return new ConcurrentWebSocketSessionDecorator(session, WS_SEND_TIME_LIMIT_MS, WS_SEND_BUFFER_LIMIT_BYTES);
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
        Map<String, Object> payload = new HashMap<>(2);
        payload.put("type", type);
        payload.put("data", data);
        return payload;
    }
}
