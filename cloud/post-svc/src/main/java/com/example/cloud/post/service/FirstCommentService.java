package com.example.cloud.post.service;

import com.example.cloud.post.entity.Postings;
import com.example.cloud.post.repository.PostingsRepository;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首评记录服务。
 * <p>
 * 帖子的「首条评论」需要单独标记，用于前端展示「沙发」等场景。
 * 用 Redis 缓存 postingsId → firstCommentId，避免每次访问帖子查 DB。
 */
@Service
public class FirstCommentService {

    private static final String KEY_PREFIX = "post:first-comment:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PostingsRepository postingsRepository;

    /**
     * 把指定评论设为帖子的首评。
     *
     * @return true 表示这条评论确实是首评（DB 中第一次写入），false 表示已有首评
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean recordFirstComment(Long postingsId, Long commentId) {
        if (postingsId == null || commentId == null) return false;
        Postings post = postingsRepository.findById(postingsId).orElse(null);
        if (post == null) return false;

        if (post.getFirstCommentId() == null) {
            post.setFirstCommentId(commentId);
            postingsRepository.save(post);
            cacheFirstCommentId(postingsId, commentId);
            return true;
        }

        // 已有首评，写一下缓存供下次读
        Long cached = getCachedFirstCommentId(postingsId);
        if (cached == null) cacheFirstCommentId(postingsId, post.getFirstCommentId());
        return false;
    }

    public Long getFirstCommentId(Long postingsId) {
        if (postingsId == null) return null;
        Long cached = getCachedFirstCommentId(postingsId);
        if (cached != null) return cached;
        Postings post = postingsRepository.findById(postingsId).orElse(null);
        if (post != null && post.getFirstCommentId() != null) {
            cacheFirstCommentId(postingsId, post.getFirstCommentId());
            return post.getFirstCommentId();
        }
        return null;
    }

    private void cacheFirstCommentId(Long postingsId, Long commentId) {
        if (commentId == null) return;
        stringRedisTemplate.opsForValue().set(key(postingsId), String.valueOf(commentId));
    }

    private Long getCachedFirstCommentId(Long postingsId) {
        String value = stringRedisTemplate.opsForValue().get(key(postingsId));
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String key(Long postingsId) {
        return KEY_PREFIX + postingsId;
    }
}
