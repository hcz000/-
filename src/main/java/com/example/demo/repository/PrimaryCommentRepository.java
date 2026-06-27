package com.example.demo.repository;

import com.example.demo.entity.PrimaryComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PrimaryCommentRepository extends JpaRepository<PrimaryComment, Long>, JpaSpecificationExecutor<PrimaryComment> {

    // 列表查询时一次拉取评论作者，避免按 user_id 逐条触发 N+1。
    @Override
    @EntityGraph(attributePaths = {"author"})
    Page<PrimaryComment> findAll(Specification<PrimaryComment> spec, Pageable pageable);

    /**
     * 增量更新点赞数
     */
    @Modifying
    @Query(value = "UPDATE primary_comment SET like_count = like_count + :delta WHERE id = :id AND deleted = false", nativeQuery = true)
    int incrementLikeCount(@Param("id") Long id, @Param("delta") int delta);
}