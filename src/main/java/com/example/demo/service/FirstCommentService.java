package com.example.demo.service;

import com.example.demo.entity.Postings;
import com.example.demo.repository.PostingsRepository;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FirstCommentService {

    private static final String KEY_PREFIX = "post:first-comment:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private PostingsRepository postingsRepository;


    @Transactional
    public boolean recordFirstComment(Long postingsId, Long commentId) {
        if (postingsId == null || commentId == null) {
            return false;
        }

        Postings post = postingsRepository.findById(postingsId).orElse(null);
        if (post == null) {
            return false;
        }

        if (post.getFirstCommentId() == null) {
            post.setFirstCommentId(commentId);
            postingsRepository.save(post);
            cacheFirstCommentId(postingsId, commentId);
            return true;
        }

        Long cached = getCachedFirstCommentId(postingsId);
        if (cached != null) {
            return false;
        }

        cacheFirstCommentId(postingsId, post.getFirstCommentId());
        return false;
    }

    public Long getFirstCommentId(Long postingsId) {
        if (postingsId == null) {
            return null;
        }
        Long cached = getCachedFirstCommentId(postingsId);
        if (cached != null) {
            return cached;
        }
        Postings post = postingsRepository.findById(postingsId).orElse(null);
        if (post != null && post.getFirstCommentId() != null) {
            cacheFirstCommentId(postingsId, post.getFirstCommentId());
            return post.getFirstCommentId();
        }
        return null;
    }

    private void cacheFirstCommentId(Long postingsId, Long commentId) {
        if (commentId == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key(postingsId), String.valueOf(commentId));
    }

    private Long getCachedFirstCommentId(Long postingsId) {
        String value = stringRedisTemplate.opsForValue().get(key(postingsId));
        if (value == null) {
            return null;
        }
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