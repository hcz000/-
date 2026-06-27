package com.example.demo.repository;

import com.example.demo.entity.SecondaryComment;
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

import java.util.List;

@Repository
public interface SecondaryCommentRepository extends JpaRepository<SecondaryComment, Long>, JpaSpecificationExecutor<SecondaryComment> {

    // 列表查询时一次拉取评论作者，避免按 user_id 逐条触发 N+1。
    @Override
    @EntityGraph(attributePaths = {"author"})
    Page<SecondaryComment> findAll(Specification<SecondaryComment> spec, Pageable pageable);

    /**
     * 增量更新点赞数
     */
    @Modifying
    @Query(value = "UPDATE secondary_comment SET like_count = like_count + :delta WHERE id = :id AND deleted = false", nativeQuery = true)
    int incrementLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Query(value = """
            SELECT primary_comment_id, COUNT(*)
            FROM secondary_comment
            WHERE deleted = false
              AND primary_comment_id IN (:primaryCommentIds)
            GROUP BY primary_comment_id
            """, nativeQuery = true)
    List<Object[]> countByPrimaryCommentIds(@Param("primaryCommentIds") List<Long> primaryCommentIds);
}
