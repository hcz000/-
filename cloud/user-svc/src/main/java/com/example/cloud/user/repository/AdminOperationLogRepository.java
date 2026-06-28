package com.example.cloud.user.repository;

import com.example.cloud.user.entity.AdminOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {
}
