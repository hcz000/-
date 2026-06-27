package com.example.demo.service;

import com.example.demo.entity.Postings;
import com.example.demo.entity.dto.CursorPage;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;


public interface IPostingsService {

    Postings createPost(Postings postings);

    /**
     * 注意：返回的 Postings 来自 @Cacheable(CACHE_POSTINGS) 本地缓存，
     * 其 author / planet 关联字段是未初始化的懒代理。由于 open-in-view=false，
     * 控制器层访问 post.getAuthor() 会抛 LazyInitializationException。
     * 如需访问关联，请改造此方法用 @EntityGraph 一并加载，并清理已有缓存。
     */
    Postings getPost(Long postingsId);

    Postings updatePost(Postings postings);

    void removePost(Long postingsId);

    Page<Postings> listByPlanetId(Long planetId, int pageNum, int pageSize);

    /**
     * 游标分页查询星球帖子（按创建时间倒序）
     *
     * @param planetId 星球 ID
     * @param cursor   上一页最后一条的 createTime（首页传 null）
     * @param lastId   上一页最后一条的 postingsId（首页传 null）
     * @param size     每页大小
     */
    CursorPage<Postings> listByPlanetIdCursor(Long planetId, LocalDateTime cursor, Long lastId, int size);

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