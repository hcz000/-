package com.example.cloud.post.repository;

import com.example.cloud.post.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * 取最旧的若干条 PENDING 事件供调度器投递。
     * <p>
     * 用 status + created_at 复合索引覆盖，O(log n) 走索引扫描。
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = com.example.cloud.post.entity.OutboxEvent$Status.PENDING " +
            "ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingBatch(Pageable pageable);

    long countByStatus(@Param("status") OutboxEvent.Status status);
}
