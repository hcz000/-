package com.example.demo.task;

import com.example.demo.entity.Postings;
import com.example.demo.repository.PostingsRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class ReplyCountSyncTask {

    private static final int BATCH_SIZE = 100;
    private static final String REPLY_COUNT_KEY_PREFIX = "postings:reply_count:";
    private static final String REPLY_COUNT_DIRTY_ZSET = "postings:reply_count:dirty";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private PostingsRepository postingsRepository;

    @Scheduled(fixedDelayString = "${reply-count.sync.delay:60000}")
    @Transactional
    public void syncReplyCounts() {
        while (true) {
            Set<String> postingsIds = stringRedisTemplate.opsForZSet()
                    .range(REPLY_COUNT_DIRTY_ZSET, 0, BATCH_SIZE - 1);
            if (postingsIds == null || postingsIds.isEmpty()) {
                return;
            }
            List<Object> processed = new ArrayList<>(postingsIds.size());
            for (String postingsId : postingsIds) {
                try {
                    long id = Long.parseLong(postingsId);
                    String countValue = stringRedisTemplate.opsForValue().get(REPLY_COUNT_KEY_PREFIX + postingsId);
                    if (!StringUtils.hasText(countValue)) {
                        processed.add(postingsId);
                        continue;
                    }
                    int count = Integer.parseInt(countValue);
                    Postings post = postingsRepository.findById(id).orElse(null);
                    if (post != null && !Boolean.TRUE.equals(post.getDeleted())) {
                        post.setReplyCount(count);
                        postingsRepository.save(post);
                    }
                    processed.add(postingsId);
                } catch (NumberFormatException e) {
                    log.warn("回复数集合中存在无效的帖子 ID: {}", postingsId, e);
                    processed.add(postingsId);
                } catch (Exception e) {
                    log.warn("同步帖子 {} 的回复数失败", postingsId, e);
                }
            }
            if (!processed.isEmpty()) {
                stringRedisTemplate.opsForZSet().remove(REPLY_COUNT_DIRTY_ZSET, processed.toArray());
            }
        }
    }
}