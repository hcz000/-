package com.example.demo.service;

import com.example.demo.entity.PrimaryComment;
import org.springframework.data.domain.Page;

/**
 * 一级评论 服务类
 */
public interface IPrimaryCommentService {

    PrimaryComment createComment(PrimaryComment comment);

    void removeComment(Long commentId);

    Page<PrimaryComment> listByPostings(Long postingsId, int pageNum, int pageSize);

    int toggleLike(Long commentId, Long userId, boolean like);

    /**
     * 获取评论列表（后台管理用，支持多条件筛选）
     */
    Page<PrimaryComment> getCommentList(Long postId, Long userId, int pageNum, int pageSize);

    PrimaryComment save(PrimaryComment comment);

    PrimaryComment getById(Long id);

    boolean updateById(PrimaryComment comment);
}