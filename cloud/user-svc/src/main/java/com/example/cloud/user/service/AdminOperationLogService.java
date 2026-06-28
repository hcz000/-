package com.example.cloud.user.service;

import com.example.cloud.user.entity.AdminOperationLog;
import com.example.cloud.user.repository.AdminOperationLogRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationLogService {

    @Resource
    private AdminOperationLogRepository adminOperationLogRepository;

    public void record(Long adminId, String action, String targetType, Long targetId, String detail) {
        if (adminId == null || action == null || action.isBlank()) return;
        AdminOperationLog log = new AdminOperationLog()
                .setAdminId(adminId)
                .setAction(action)
                .setTargetType(targetType)
                .setTargetId(targetId)
                .setDetail(detail);
        adminOperationLogRepository.save(log);
    }
}
