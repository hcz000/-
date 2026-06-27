package com.example.cloud.common.api.post;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子发布事件（跨服务消息载荷）。
 * <p>
 * 发送方：post-svc（在创建帖子的本地事务里写 outbox，调度器异步发到 MQ）
 * 订阅方：push-svc（消费后调 CandidatePoolService.addToPools）
 *
 * <h3>消息体设计</h3>
 * 只携带 push-svc 加入候选池必需的最少字段，详情字段由消费方按 postId 回查 DB。
 * 这样消息和 DB 之间永远是「事件触发 + 拉取最新数据」的模型，避免消息里携带的快照过期。
 *
 * <h3>幂等性</h3>
 * {@link #eventId} 是全局唯一 ID（建议 UUID），消费方用 Redis Set 做去重。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCreatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件全局唯一 ID（UUID），消费方用它做幂等去重 */
    private String eventId;

    /** 帖子 ID */
    private Long postId;

    /** 帖子作者 ID */
    private Long userId;

    /** 所属星球 ID（可选） */
    private Long planetId;

    /** 帖子类型（push-svc 用来选 type pool） */
    private String type;

    /** 事件产生时间 */
    private LocalDateTime occurredAt;

    /** 事件路由键 */
    public static final String ROUTING_KEY = "post.created";

    /** 事件类型（写入 outbox.event_type 字段） */
    public static final String EVENT_TYPE = "POST_CREATED";
}
