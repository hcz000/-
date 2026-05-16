package com.example.demo.repository;

import com.example.demo.entity.PrimaryComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PrimaryCommentRepository extends JpaRepository<PrimaryComment, Long>, JpaSpecificationExecutor<PrimaryComment> {

    /**
     * 增量更新点赞数
     */
    @Modifying
    @Query(value = "UPDATE primary_comment SET like_count = like_count + :delta WHERE id = :id AND deleted = false", nativeQuery = true)
    int incrementLikeCount(@Param("id") Long id, @Param("delta") int delta);
}