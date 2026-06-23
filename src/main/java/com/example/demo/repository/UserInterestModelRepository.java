package com.example.demo.repository;

import com.example.demo.entity.UserInterestModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserInterestModelRepository extends JpaRepository<UserInterestModel, Long> {

    List<UserInterestModel> findByUserId(Long userId);

    Optional<UserInterestModel> findByUserIdAndInterestKey(Long userId, String interestKey);
}
