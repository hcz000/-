package com.example.cloud.post.repository;

import com.example.cloud.post.entity.SecondaryComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecondaryCommentRepository extends JpaRepository<SecondaryComment, Long>, JpaSpecificationExecutor<SecondaryComment> {

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
