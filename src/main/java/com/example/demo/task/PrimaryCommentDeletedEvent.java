package com.example.demo.task;

/**
 * 一级评论删除事件 - 触发二级评论级联清理
 * <p>
 * 替代原 RabbitMQ 的 primary-comment-delete 队列：单 JVM 内无需绕 broker，
 * 用 Spring 事件 + @TransactionalEventListener(AFTER_COMMIT) 既省一次 JSON 序列化和网络往返，
 * 又能保证「主事务回滚则监听器不触发」，比原 MQ 方案少一个数据错乱窗口。
 */
public record PrimaryCommentDeletedEvent(Long primaryCommentId) {
}
