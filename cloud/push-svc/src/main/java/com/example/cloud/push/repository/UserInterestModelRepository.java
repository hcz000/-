package com.example.cloud.push.repository;

import com.example.cloud.push.entity.UserInterestModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserInterestModelRepository extends JpaRepository<UserInterestModel, Long> {

    List<UserInterestModel> findByUserId(Long userId);

    Optional<UserInterestModel> findByUserIdAndInterestKey(Long userId, String interestKey);
}
