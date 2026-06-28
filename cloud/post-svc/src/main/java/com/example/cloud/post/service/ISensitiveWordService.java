package com.example.cloud.post.service;

import java.util.List;
import java.util.Set;

/**
 * 敏感词服务。
 */
public interface ISensitiveWordService {

    boolean containsSensitiveWord(String text);

    Set<String> findSensitiveWords(String text);

    String filterSensitiveWords(String text, char replacement);

    String highlightSensitiveWords(String text);

    void addSensitiveWord(String word);

    void removeSensitiveWord(String word);

    List<String> getAllSensitiveWords();
}
