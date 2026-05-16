package com.example.demo.service;

import com.example.demo.entity.Postings;
import org.springframework.data.domain.Page;


public interface IPostingsService {

    Postings createPost(Postings postings);

    Postings getPost(Long postingsId);

    Postings updatePost(Postings postings);

    void removePost(Long postingsId);

    Page<Postings> listByPlanetId(Long planetId, int pageNum, int pageSize);

    int togglePostLike(Long postingsId, Long userId, boolean like);

    Page<Postings> searchPosts(String keyword, Long planetId, int pageNum, int pageSize);

    Page<Postings> listLikedPosts(Long userId, int pageNum, int pageSize);

    void adjustReplyCount(Long postingsId, int delta);

    /**
     * 获取帖子列表（后台管理用，支持多条件筛选）
     */
    Page<Postings> getPostList(Long planetId, Long userId, String keyword, int pageNum, int pageSize);

    Postings save(Postings postings);

    Postings getById(Long id);

    boolean updateById(Postings postings);
}