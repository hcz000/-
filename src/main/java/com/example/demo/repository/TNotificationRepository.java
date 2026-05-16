package com.example.demo.repository;

import com.example.demo.entity.TNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TNotificationRepository extends JpaRepository<TNotification, Long>, JpaSpecificationExecutor<TNotification> {
}