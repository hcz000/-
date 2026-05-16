package com.example.demo.service;

import com.example.demo.entity.SecondaryComment;
import org.springframework.data.domain.Page;

/**
 * 二级评论（单表版） 服务类
 */
public interface ISecondaryCommentService {

    SecondaryComment createComment(SecondaryComment comment);

    void removeComment(Long commentId);

    Page<SecondaryComment> listByPrimaryComment(Long primaryCommentId, int pageNum, int pageSize);

    int toggleLike(Long commentId, Long userId, boolean like);

    SecondaryComment save(SecondaryComment comment);

    SecondaryComment getById(Long id);

    boolean updateById(SecondaryComment comment);
}