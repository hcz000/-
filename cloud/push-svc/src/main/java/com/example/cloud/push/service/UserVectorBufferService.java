package com.example.cloud.push.service;

import com.example.cloud.push.enums.PostingType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 用户兴趣向量 buffer：用户行为（点赞/评论等）先入 Redis 队列，
 * 由 {@link com.example.cloud.push.task.UserVectorFlushTask} 周期性批量 flush 到 DB。
 * <p>
 * 当前简化版的 LikeUserSyncService（post-svc）没接入这个 buffer（标了 TODO），
 * 但本服务依然保留完整能力，供后续事件桥接接通后启用。
 */
@Slf4j
@Service
public class UserVectorBufferService {

    private static final String USER_VECTOR_DIRTY_KEY = "user:vector:dirty";
    private static final String USER_VECTOR_EVENT_LIST_PREFIX = "user:vector:events:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserVectorService userVectorService;

    private DefaultRedisScript<Long> trimEventsScript;
    private DefaultRedisScript<Long> finalizeFlushScript;

    @PostConstruct
    public void initScripts() {
        trimEventsScript = new DefaultRedisScript<>();
        trimEventsScript.setLocation(new ClassPathResource("lua/trim_events.lua"));
        trimEventsScript.setResultType(Long.class);

        finalizeFlushScript = new DefaultRedisScript<>();
        finalizeFlushScript.setLocation(new ClassPathResource("lua/finalize_flush.lua"));
        finalizeFlushScript.setResultType(Long.class);
    }

    public void enqueue(Long userId, PostingType postingType) {
        if (userId == null || postingType == null) return;
        String userIdStr = userId.toString();
        String listKey = userEventListKey(userIdStr);
        stringRedisTemplate.opsForList().rightPush(listKey, postingType.name());
        stringRedisTemplate.opsForZSet().add(USER_VECTOR_DIRTY_KEY, userIdStr, System.currentTimeMillis());
    }

    public FlushStats flushOnce(int maxUsers, int maxEventsPerUser) {
        if (maxUsers <= 0 || maxEventsPerUser <= 0) return new FlushStats(0, 0);
        Set<String> userIds = stringRedisTemplate.opsForZSet().range(USER_VECTOR_DIRTY_KEY, 0, maxUsers - 1);
        if (userIds == null || userIds.isEmpty()) return new FlushStats(0, 0);

        int users = 0, events = 0;
        for (String userIdStr : userIds) {
            long userId;
            try {
                userId = Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                stringRedisTemplate.opsForZSet().remove(USER_VECTOR_DIRTY_KEY, userIdStr);
                continue;
            }
            UserFlushResult result = flushSingleUser(userIdStr, userId, maxEventsPerUser);
            if (result.appliedEvents() > 0) {
                users++;
                events += result.appliedEvents();
            }
        }
        return new FlushStats(users, events);
    }

    private UserFlushResult flushSingleUser(String userIdStr, Long userId, int maxEventsPerUser) {
        String listKey = userEventListKey(userIdStr);
        List<String> rawEvents = readEvents(listKey, maxEventsPerUser);
        if (rawEvents.isEmpty()) {
            finalizeUserFlush(listKey, userIdStr);
            return new UserFlushResult(0);
        }

        List<PostingType> postingTypes = new ArrayList<>(rawEvents.size());
        for (String rawType : rawEvents) {
            try {
                PostingType type = PostingType.valueOf(rawType);
                postingTypes.add(type);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown buffered posting type, userId={}, rawType={}", userId, rawType);
            }
        }

        int appliedEvents = userVectorService.applyUserVectorBatch(userId, postingTypes);

        if (appliedEvents > 0) {
            trimProcessedEvents(listKey, rawEvents.size());
            userVectorService.evictTypeRatiosCache(userId);
        }
        finalizeUserFlush(listKey, userIdStr);
        return new UserFlushResult(appliedEvents);
    }

    private List<String> readEvents(String listKey, int maxEvents) {
        List<String> events = stringRedisTemplate.opsForList().range(listKey, 0, maxEvents - 1);
        return events == null || events.isEmpty() ? Collections.emptyList() : events;
    }

    private void trimProcessedEvents(String listKey, int count) {
        if (count <= 0) return;
        stringRedisTemplate.execute(trimEventsScript,
                Collections.singletonList(listKey), String.valueOf(count));
    }

    private void finalizeUserFlush(String listKey, String userIdStr) {
        stringRedisTemplate.execute(finalizeFlushScript,
                List.of(listKey, USER_VECTOR_DIRTY_KEY),
                userIdStr, String.valueOf(System.currentTimeMillis()));
    }

    private String userEventListKey(String userId) {
        return USER_VECTOR_EVENT_LIST_PREFIX + userId;
    }

    public record FlushStats(int users, int events) {}

    private record UserFlushResult(int appliedEvents) {}
}
