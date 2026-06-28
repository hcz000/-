package com.example.cloud.user.repository;

import com.example.cloud.user.entity.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long>, JpaSpecificationExecutor<FriendRelation> {

    @Query("SELECT fr FROM FriendRelation fr WHERE (fr.userAId = :userId OR fr.userBId = :userId) AND fr.deleted = false")
    List<FriendRelation> findByUserIdInRelation(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(fr) > 0 THEN true ELSE false END FROM FriendRelation fr WHERE fr.userAId = :userA AND fr.userBId = :userB AND fr.deleted = false")
    boolean existsByUserPair(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("SELECT fr FROM FriendRelation fr WHERE fr.userAId = :userA AND fr.userBId = :userB")
    Optional<FriendRelation> findByUserPair(@Param("userA") Long userA, @Param("userB") Long userB);
}
