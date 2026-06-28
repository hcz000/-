package com.example.cloud.post.repository;

import com.example.cloud.post.entity.PlanetMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanetMemberRepository extends JpaRepository<PlanetMember, Long> {

    boolean existsByPlanetIdAndUserId(Long planetId, Long userId);

    @Query("SELECT pm.userId FROM PlanetMember pm WHERE pm.planetId = :planetId")
    List<Long> findUserIdsByPlanetId(@Param("planetId") Long planetId);

    @Query("SELECT pm.planetId FROM PlanetMember pm WHERE pm.userId = :userId")
    List<Long> findPlanetIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT pm.planetId FROM PlanetMember pm WHERE pm.userId = :userId")
    Page<Long> findPlanetIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    int countByPlanetId(Long planetId);

    int deleteByPlanetIdAndUserId(Long planetId, Long userId);
}
