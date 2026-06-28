package com.example.cloud.post.constant;

/**
 * post-svc 内部 RabbitMQ 队列/路由 key 常量。
 * <p>
 * 注：跨服务消息（NotificationEvent / PostCreatedEvent）有专门的常量；
 * 这里集中的是 post-svc 内部 buffer 同步类的队列名。
 */
public final class CommentTopics {

    private CommentTopics() {}

    public static final String LIKE_SYNC = "like-sync";
    public static final String LIKE_USER_SYNC = "like-user-sync";
}
