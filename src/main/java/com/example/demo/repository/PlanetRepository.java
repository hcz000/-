package com.example.demo.repository;

import com.example.demo.entity.Planet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanetRepository extends JpaRepository<Planet, Long>, JpaSpecificationExecutor<Planet> {

    @Query(value = """
            SELECT planet_id, name, description, member_count, master, category, create_time, update_time, deleted, status,
                   MATCH(name, description, category) AGAINST (:keyword IN NATURAL LANGUAGE MODE) AS relevance
            FROM planet
            WHERE deleted = false
              AND (
                    MATCH(name, description, category) AGAINST (:keyword IN NATURAL LANGUAGE MODE)
                 OR name LIKE CONCAT('%', :keyword, '%')
                 OR description LIKE CONCAT('%', :keyword, '%')
                 OR category LIKE CONCAT('%', :keyword, '%')
              )
            ORDER BY relevance DESC, create_time DESC
            LIMIT :pageSize OFFSET :offset
            """, nativeQuery = true)
    List<Planet> searchByFullText(@Param("keyword") String keyword,
                                  @Param("pageSize") long pageSize,
                                  @Param("offset") long offset);
}
