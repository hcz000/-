package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    @Query(value = "SELECT * FROM users WHERE id = :id FOR UPDATE", nativeQuery = true)
    User selectByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT id, username, password, email, avatar, create_time, update_time, liketype, role, deleted,
                   GREATEST(similarity(coalesce(username, ''), :keyword),
                            similarity(coalesce(email, ''), :keyword)) as relevance
            FROM users
            WHERE deleted = false
              AND (
                    coalesce(username, '') ILIKE CONCAT('%', :keyword, '%')
                 OR coalesce(email, '') ILIKE CONCAT('%', :keyword, '%')
              )
            ORDER BY relevance DESC, create_time DESC
            LIMIT :pageSize OFFSET :offset
            """, nativeQuery = true)
    List<User> searchByFullText(@Param("keyword") String keyword,
                                @Param("pageSize") long pageSize,
                                @Param("offset") long offset);
}
