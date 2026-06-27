package com.example.cloud.push.repository;

import com.example.cloud.push.entity.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {

    @Query("SELECT fr FROM FriendRelation fr WHERE (fr.userAId = :userId OR fr.userBId = :userId) AND fr.deleted = false")
    List<FriendRelation> findByUserIdInRelation(@Param("userId") Long userId);
}
