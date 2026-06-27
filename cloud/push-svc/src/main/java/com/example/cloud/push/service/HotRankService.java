package com.example.cloud.push.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 热门榜服务 —— STUB 版。
 * <p>
 * 单体版基于 Redis ZSet 计算热度分数（点赞数 / 评论数 / 时间衰减），
 * 依赖 {@code LikeService} / {@code SystemConfigService} 等多个服务。
 * <p>
 * 当前微服务骨架阶段返回空列表，相当于关闭「热门补全」路径。
 * TODO 后续把真实实现迁过来。
 */
@Slf4j
@Service
public class HotRankService {

    public List<Long> topIds(int limit) {
        log.debug("[hot:stub] topIds(limit={}) returns empty in skeleton phase", limit);
        return Collections.emptyList();
    }

    public void rebuildRecent(int limit) {
        log.debug("[hot:stub] rebuildRecent(limit={}) no-op in skeleton phase", limit);
    }
}
