package com.example.cloud.common.api.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通知事件（跨服务消息载荷）。
 * <p>
 * 任意服务想发通知都通过 RabbitMQ topic exchange {@code notification.events} 发送，
 * user-svc 的 NotificationEventListener 统一消费、落库、推 WebSocket。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发送方 userId（系统通知为 null） */
    private Long senderId;

    /** 接收方 userId（必填） */
    private Long recipientId;

    /** 通知类型 code，对应 {@link NotificationType#getCode()} */
    private Integer type;

    /** 相关帖子 ID（可选） */
    private Long postId;

    /** 相关业务对象 ID 或链接（可选，如评论 id、好友请求 id） */
    private String relatedId;

    /** 通知正文 */
    private String content;

    /** 网关交换机名 */
    public static final String EXCHANGE = "notification.events";

    /** user-svc 监听的队列 */
    public static final String QUEUE = "notification.user-svc.queue";

    /** 路由键 */
    public static final String ROUTING_KEY = "notification.#";
}
