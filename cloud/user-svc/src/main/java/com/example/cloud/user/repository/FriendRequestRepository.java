package com.example.cloud.user.repository;

import com.example.cloud.user.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    @Query("SELECT fr FROM FriendRequest fr WHERE fr.targetId = :userId AND fr.status = :status ORDER BY fr.createTime DESC")
    List<FriendRequest> findByTargetIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);

    @Query("SELECT CASE WHEN COUNT(fr) > 0 THEN true ELSE false END FROM FriendRequest fr WHERE fr.requesterId = :requesterId AND fr.targetId = :targetId AND fr.status = :status")
    boolean existsPendingRequest(@Param("requesterId") Long requesterId, @Param("targetId") Long targetId, @Param("status") Integer status);
}
