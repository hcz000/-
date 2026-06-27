package com.example.cloud.post.service;

import com.example.cloud.post.repository.PostingsRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 帖子服务（最小实现：仅支持「软删除某用户全部帖子」，供账号注销级联使用）。
 * <p>
 * 后续把单体里完整的 IPostingsService 迁过来时，把这个合并即可。
 */
@Slf4j
@Service
public class PostService {

    @Resource
    private PostingsRepository postingsRepository;

    /**
     * 软删除某用户名下的所有帖子。
     * <p>
     * 加 {@link Transactional} 让本地 UPDATE 进入一个本地事务，
     * Seata AT 在此基础上构建分支事务（写 undo_log）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int softDeletePostsByUser(Long userId) {
        if (userId == null) return 0;
        int affected = postingsRepository.softDeleteByUserId(userId);
        log.info("[post] soft delete posts by userId={}, affected={}", userId, affected);
        return affected;
    }

    public long countLivePostsOfUser(Long userId) {
        return userId == null ? 0 : postingsRepository.countByUserIdAndDeletedFalse(userId);
    }
}
