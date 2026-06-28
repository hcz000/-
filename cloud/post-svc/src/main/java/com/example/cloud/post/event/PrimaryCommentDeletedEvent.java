package com.example.cloud.post.event;

/**
 * 一级评论删除事件 → 触发二级评论级联清理。
 * <p>
 * 用 Spring 事件 + @TransactionalEventListener(AFTER_COMMIT)，
 * 单 JVM 内省一次 MQ 序列化和网络往返，且保证「主事务回滚则监听器不触发」。
 */
public record PrimaryCommentDeletedEvent(Long primaryCommentId) {
}
