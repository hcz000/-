package com.example.cloud.post.repository;

import com.example.cloud.post.entity.UserLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLikeRepository extends JpaRepository<UserLike, Long>, JpaSpecificationExecutor<UserLike> {
}
