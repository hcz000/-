package com.example.cloud.push.service;

import com.example.cloud.common.api.user.UserFeignClient;
import com.example.cloud.common.api.user.UserInfoDTO;
import com.example.cloud.push.entity.Postings;
import com.example.cloud.push.vo.PostWithAuthorVO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 帖子作者信息补全服务。
 * <p>
 * 拿到推送结果后，从 Postings 中收集 userId，通过 Feign 批量调 user-svc
 * 拿到作者脱敏信息，组装成 {@link PostWithAuthorVO} 列表返回。
 * <p>
 * 通过 Resilience4j 熔断器 {@code user-svc-cb} 保护：
 * <ul>
 *   <li>失败率超过 50%（或慢调用率超过 50%）→ 断路器开启 30s</li>
 *   <li>开启期间所有调用直接走 {@link #fallback}，返回不带 author 的 VO 列表</li>
 *   <li>30s 后进入半开状态，放行少量请求探测 user-svc 是否恢复</li>
 * </ul>
 * 详细参数见 application.yml 的 {@code resilience4j.circuitbreaker.instances.user-svc-cb}。
 */
@Slf4j
@Service
public class AuthorEnrichService {

    @Resource
    private UserFeignClient userFeignClient;

    /**
     * 把 Postings 列表补全为带作者的 VO 列表。
     * <p>
     * 整个补全过程被熔断器包裹：Feign 调用抛异常或超时都会触发 {@link #fallback}。
     */
    @CircuitBreaker(name = "user-svc-cb", fallbackMethod = "fallback")
    public List<PostWithAuthorVO> enrich(List<Postings> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集唯一的 userId（一次 RPC 批量取，避免 N+1）
        Set<Long> uniqueUserIds = new HashSet<>(posts.size());
        for (Postings p : posts) {
            if (p != null && p.getUserId() != null) {
                uniqueUserIds.add(p.getUserId());
            }
        }

        Map<Long, UserInfoDTO> authorMap;
        if (uniqueUserIds.isEmpty()) {
            authorMap = Collections.emptyMap();
        } else {
            authorMap = userFeignClient.getUsersByIds(new ArrayList<>(uniqueUserIds));
            log.debug("[author-enrich] feign call user-svc, ids={}, hit={}",
                    uniqueUserIds.size(), authorMap == null ? 0 : authorMap.size());
        }

        // 按 userId 组装作者信息
        List<PostWithAuthorVO> result = new ArrayList<>(posts.size());
        for (Postings p : posts) {
            UserInfoDTO author = (p == null || p.getUserId() == null || authorMap == null)
                    ? null : authorMap.get(p.getUserId());
            result.add(PostWithAuthorVO.of(p, author));
        }
        return result;
    }

    /**
     * 熔断降级：返回不带 author 的 VO 列表，前端按缺省策略渲染。
     * <p>
     * 注意：fallback 方法签名必须和原方法一致，最后多一个 Throwable 参数。
     */
    @SuppressWarnings("unused")
    private List<PostWithAuthorVO> fallback(List<Postings> posts, Throwable t) {
        log.warn("[author-enrich] circuit breaker fallback, reason={}", t.toString());
        if (posts == null) return Collections.emptyList();
        return posts.stream()
                .filter(Objects::nonNull)
                .map(p -> PostWithAuthorVO.of(p, null))
                .toList();
    }
}
