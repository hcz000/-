package com.example.demo.service;

import java.util.List;
import java.util.Set;

/**
 * 违禁词检查服务
 */
public interface ISensitiveWordService {

    /**
     * 检查文本是否包含违禁词
     * @param text 待检查文本
     * @return 是否包含违禁词
     */
    boolean containsSensitiveWord(String text);

    /**
     * 获取文本中的所有违禁词
     * @param text 待检查文本
     * @return 违禁词列表
     */
    Set<String> findSensitiveWords(String text);

    /**
     * 过滤文本中的违禁词（替换为指定字符）
     * @param text 待过滤文本
     * @param replacement 替换字符（如 "*"）
     * @return 过滤后的文本
     */
    String filterSensitiveWords(String text, char replacement);

    /**
     * 高亮显示违禁词（用于管理后台查看）
     * @param text 待检查文本
     * @return 高亮后的文本（违禁词用【】标记）
     */
    String highlightSensitiveWords(String text);

    /**
     * 加载违禁词库（从文件）
     * @param filePath 违禁词文件路径
     */
    void loadSensitiveWords(String filePath);

    /**
     * 添加单个违禁词
     * @param word 违禁词
     */
    void addSensitiveWord(String word);

    /**
     * 删除违禁词
     * @param word 违禁词
     */
    void removeSensitiveWord(String word);

    /**
     * 获取所有违禁词
     * @return 违禁词列表
     */
    List<String> getAllSensitiveWords();
}