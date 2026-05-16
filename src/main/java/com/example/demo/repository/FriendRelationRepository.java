package com.example.demo.repository;

import com.example.demo.entity.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long>, JpaSpecificationExecutor<FriendRelation> {

    /**
     * 查询用户的好友关系（userAId或userBId等于userId）
     */
    @Query("SELECT fr FROM FriendRelation fr WHERE (fr.userAId = :userId OR fr.userBId = :userId) AND fr.deleted = false")
    List<FriendRelation> findByUserIdInRelation(@Param("userId") Long userId);

    /**
     * 检查两个用户是否是好友（仅含未删除的关系）
     */
    @Query("SELECT CASE WHEN COUNT(fr) > 0 THEN true ELSE false END FROM FriendRelation fr WHERE fr.userAId = :userA AND fr.userBId = :userB AND fr.deleted = false")
    boolean existsByUserPair(@Param("userA") Long userA, @Param("userB") Long userB);

    /**
     * 查找两个用户的好友关系记录（含已删除的），用于恢复好友关系
     */
    @Query("SELECT fr FROM FriendRelation fr WHERE fr.userAId = :userA AND fr.userBId = :userB")
    Optional<FriendRelation> findByUserPair(@Param("userA") Long userA, @Param("userB") Long userB);
}