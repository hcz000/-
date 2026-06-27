package com.example.cloud.push.repository;

import com.example.cloud.push.entity.Postings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * push-svc 视角的 Postings 仓库，只暴露推送需要的查询。
 * <p>
 * 单体里的 admin 查询、全文搜索等不在这里。
 */
@Repository
public interface PostingsRepository extends JpaRepository<Postings, Long> {

    @Query(value = """
            SELECT postings_id
            FROM postings
            WHERE deleted = false AND status = 1 AND audit_status = 1
            ORDER BY create_time DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findRecentLiveIds(@Param("limit") int limit);

    @Query(value = """
            SELECT postings_id
            FROM postings
            WHERE deleted = false AND status = 1 AND audit_status = 1
              AND planet_id IN (:planetIds)
            ORDER BY create_time DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findRecentLiveIdsByPlanetIds(@Param("planetIds") List<Long> planetIds, @Param("limit") int limit);

    @Query(value = """
            SELECT postings_id
            FROM postings
            WHERE deleted = false AND status = 1 AND audit_status = 1
              AND user_id IN (:userIds)
            ORDER BY create_time DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findRecentLiveIdsByUserIds(@Param("userIds") List<Long> userIds, @Param("limit") int limit);
}
