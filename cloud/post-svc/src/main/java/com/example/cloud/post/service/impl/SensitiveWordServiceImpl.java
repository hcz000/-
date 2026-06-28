package com.example.cloud.post.service.impl;

import com.example.cloud.post.service.ISensitiveWordService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 敏感词服务（简化版）。
 * <p>
 * 与单体版的差异（单体 636 行）：
 * <ul>
 *   <li>去掉 DFA 字典树 + 拼音匹配（需要 pinyin4j 依赖；规模小用 String.contains 也够用）</li>
 *   <li>去掉「违禁词大全.txt」文件加载（管理后台直接增删即可）</li>
 *   <li>持久化保留 Redis Set（重启后自动恢复）</li>
 *   <li>性能在万级以下完全够用，更大规模需要 DFA / AC 自动机</li>
 * </ul>
 */
@Slf4j
@Service
public class SensitiveWordServiceImpl implements ISensitiveWordService {

    private static final String REDIS_KEY = "sensitive:words";

    /** 内存中的敏感词集合（启动时从 Redis 加载） */
    private final Set<String> words = new CopyOnWriteArraySet<>();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        Set<String> redisWords = stringRedisTemplate.opsForSet().members(REDIS_KEY);
        if (redisWords != null && !redisWords.isEmpty()) {
            words.addAll(redisWords);
            log.info("[sensitive] loaded {} words from Redis", words.size());
        }
    }

    @Override
    public boolean containsSensitiveWord(String text) {
        if (!StringUtils.hasText(text)) return false;
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    @Override
    public Set<String> findSensitiveWords(String text) {
        Set<String> hits = new HashSet<>();
        if (!StringUtils.hasText(text)) return hits;
        for (String w : words) {
            if (text.contains(w)) hits.add(w);
        }
        return hits;
    }

    @Override
    public String filterSensitiveWords(String text, char replacement) {
        if (!StringUtils.hasText(text)) return text;
        String result = text;
        for (String w : words) {
            if (result.contains(w)) {
                String stars = String.valueOf(replacement).repeat(w.length());
                result = result.replace(w, stars);
            }
        }
        return result;
    }

    @Override
    public String highlightSensitiveWords(String text) {
        if (!StringUtils.hasText(text)) return text;
        String result = text;
        for (String w : words) {
            if (result.contains(w)) result = result.replace(w, "【" + w + "】");
        }
        return result;
    }

    @Override
    public void addSensitiveWord(String word) {
        if (!StringUtils.hasText(word)) return;
        String trimmed = word.trim();
        words.add(trimmed);
        stringRedisTemplate.opsForSet().add(REDIS_KEY, trimmed);
    }

    @Override
    public void removeSensitiveWord(String word) {
        if (!StringUtils.hasText(word)) return;
        String trimmed = word.trim();
        words.remove(trimmed);
        stringRedisTemplate.opsForSet().remove(REDIS_KEY, trimmed);
    }

    @Override
    public List<String> getAllSensitiveWords() {
        return new ArrayList<>(words);
    }
}
