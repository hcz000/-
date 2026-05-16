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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Resource
    private ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Resource
    private ChatService chatService;

    /**
     * 当 WebSocket 连接建立后调用。
     * <p>
     * 该方法会检查用户是否已登录（通过 session 中的 userId 判断），如果未登录则关闭连接；
     * 否则将当前 session 添加到对应用户的 session 集合中，并向客户端发送连接成功的响应。
     *
     * @param session 已建立的 WebSocket 会话对象
     * @throws Exception 处理过程中可能抛出的异常
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userKey = userKey(session);
        if (!StringUtils.hasText(userKey)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        sessions.computeIfAbsent(userKey, key -> ConcurrentHashMap.newKeySet()).add(session);
        send(session, infoResponse("连接成功"));
    }

    /**
     * 处理从客户端接收到的文本消息。
     * <p>
     * 检查用户是否已登录，若未登录则关闭连接；否则尝试解析消息内容为聊天请求，
     * 调用服务层发送消息并广播给相关用户。处理过程中捕获业务异常、IO 异常和其他异常，
     * 并向客户端返回相应的错误信息。
     *
     * @param session 当前 WebSocket 会话
     * @param message 接收的文本消息对象
     * @throws Exception 处理过程中可能抛出的异常
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = userId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        try {
            // 解析消息体为聊天请求对象
            ChatMessageSendRequest payload = objectMapper.readValue(message.getPayload(), ChatMessageSendRequest.class);
            // 发送消息并获取保存后的实体
            ChatMessage saved = chatService.sendMessage(userId, payload.getFriendId(), payload.getContent());
            // 构造响应数据并广播给双方
            Map<String, Object> response = chatResponse(ChatMessageResponse.from(saved));
            broadcast(String.valueOf(userId), response);
            broadcast(String.valueOf(payload.getFriendId()), response);
        } catch (BusinessException e) {
            send(session, errorResponse(e.getMessage()));
        } catch (IOException e) {
            send(session, errorResponse("IO异常"));
        } catch (Exception e) {
            send(session, errorResponse("发送失败"));
        }
    }

    /**
     * 在 WebSocket 连接关闭后调用。
     * <p>
     * 移除该 session 对应的所有引用，释放资源。
     *
     * @param session 已关闭的 WebSocket 会话
     * @param status  关闭状态码及原因
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    /**
     * 处理传输过程中的错误。
     * <p>
     * 出现异常时移除对应的 session。
     *
     * @param session   出现问题的 WebSocket 会话
     * @param exception 异常对象
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeSession(session);
    }

    /**
     * 将指定的消息广播给某个用户的所有活跃连接。
     * <p>
     * 若用户 ID 无效或无活跃连接，则直接返回。
     *
     * @param userId   用户唯一标识符
     * @param response 待发送的 WebSocket 响应对象
     */
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
            if (webSocketSession.isOpen()) {
                try {
                    webSocketSession.sendMessage(textMessage);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void broadcastNotification(Long userId, NotificationResponse response) {
        if (userId == null) {
            return;
        }
        broadcast(String.valueOf(userId), notificationResponse(response));
    }

    /**
     * 向单个 WebSocket 会话发送消息。
     * <p>
     * 若会话为空或已关闭，则不执行任何操作。
     *
     * @param session  目标 WebSocket 会话
     * @param response 待发送的 WebSocket 响应对象
     */
    private void send(WebSocketSession session, Map<String, Object> response) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(toText(response));
        } catch (IOException ignored) {
        }
    }

    /**
     * 从内存集合中移除指定的 WebSocket 会话。
     * <p>
     * 根据 session 获取其所属用户 ID，然后在该用户的 session 集合中删除此 session。
     * 如果该用户再无其他 session，则同时清理整个用户条目。
     *
     * @param session 待移除的 WebSocket 会话
     */
    private void removeSession(WebSocketSession session) {
        String userKey = userKey(session);
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userKey);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userKey);
            }
        }
    }

    /**
     * 从 WebSocket 会话属性中提取用户 ID。
     * <p>
     * 使用预定义的键名从 session 属性中获取用户 ID 字符串。
     *
     * @param session WebSocket 会话对象
     * @return 用户 ID 字符串，如不存在则返回 null
     */
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

       /**
     * 获取WebSocket会话对应的用户标识键值
     *
     * @param session WebSocket会话对象，用于获取用户标识信息
     * @return 返回用户标识的字符串表示，如果会话中没有用户标识则返回null
     */
    private String userKey(WebSocketSession session) {
        // 从会话属性中获取用户ID属性值
        Object value = session.getAttributes().get(SaTokenHandshakeInterceptor.USER_ID_ATTR);
        // 将属性值转换为字符串形式返回，如果属性值为空则返回null
        return value == null ? null : value.toString();
    }

    private TextMessage toText(Map<String, Object> response) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("无法序列化", e);
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

