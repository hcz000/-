package com.example.cloud.post.repository;

import com.example.cloud.post.entity.Postings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostingsRepository extends JpaRepository<Postings, Long> {

    /**
     * 软删除某个用户的所有帖子，返回受影响行数。
     * <p>
     * 用于「账号注销级联清理」场景，由 Seata AT 模式保护。
     * 注意：这里走 UPDATE（不是 DELETE），便于 AT 模式快照回滚。
     */
    @Modifying
    @Query("UPDATE Postings p SET p.deleted = true WHERE p.userId = :userId AND p.deleted = false")
    int softDeleteByUserId(@Param("userId") Long userId);

    long countByUserIdAndDeletedFalse(Long userId);
}
