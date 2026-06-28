package com.example.cloud.post.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 后台管理操作审计日志（post-svc 端简化版）。
 * <p>
 * 单体里 AdminOperationLogService 直接写 admin_operation_log 表（在 user-svc 域）。
 * 微服务拆分后 post-svc 不持有该表，通过 RabbitMQ 异步发送日志事件到 user-svc 落库。
 * 当前版本简化为直接 log.info（生产建议改为 MQ 事件）。
 */
@Slf4j
@Service
public class AdminOperationLogService {

    @Resource(name = "rabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    public void record(Long adminId, String action, String targetType, Long targetId, String detail) {
        // TODO 通过 MQ 发到 user-svc 落到 admin_operation_log 表
        log.info("[admin-op] adminId={}, action={}, targetType={}, targetId={}, detail={}",
                adminId, action, targetType, targetId, detail);
    }
}
