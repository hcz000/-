package com.example.cloud.post.repository;

import com.example.cloud.post.entity.Postings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostingsRepository extends JpaRepository<Postings, Long>, JpaSpecificationExecutor<Postings> {

    /**
     * 软删除某个用户的所有帖子，返回受影响行数。
     */
    @Modifying
    @Query("UPDATE Postings p SET p.deleted = true WHERE p.userId = :userId AND p.deleted = false")
    int softDeleteByUserId(@Param("userId") Long userId);

    long countByUserIdAndDeletedFalse(Long userId);

    @Modifying
    @Query(value = "UPDATE postings SET like_count = like_count + :delta WHERE postings_id = :id AND deleted = false", nativeQuery = true)
    int incrementLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Query(value = """
            SELECT postings_id, planet_id, title, content, user_id, status, reply_count, like_count,
                   first_comment_id, create_time, update_time, type, deleted, audit_status, images,
                   MATCH(title, content) AGAINST (:keyword IN NATURAL LANGUAGE MODE) AS relevance
            FROM postings
            WHERE deleted = false AND status = 1 AND audit_status = 1
              AND (
                    MATCH(title, content) AGAINST (:keyword IN NATURAL LANGUAGE MODE)
                 OR title LIKE CONCAT('%', :keyword, '%')
                 OR content LIKE CONCAT('%', :keyword, '%')
              )
            ORDER BY relevance DESC, create_time DESC
            LIMIT :pageSize OFFSET :offset
            """, nativeQuery = true)
    List<Postings> searchByFullText(@Param("keyword") String keyword,
                                    @Param("pageSize") long pageSize,
                                    @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*)
            FROM postings
            WHERE deleted = false AND status = 1 AND audit_status = 1
              AND (
                    MATCH(title, content) AGAINST (:keyword IN NATURAL LANGUAGE MODE)
                 OR title LIKE CONCAT('%', :keyword, '%')
                 OR content LIKE CONCAT('%', :keyword, '%')
              )
            """, nativeQuery = true)
    Long countSearchResults(@Param("keyword") String keyword);

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
