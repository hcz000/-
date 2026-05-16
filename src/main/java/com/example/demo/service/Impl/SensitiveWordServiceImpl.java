package com.example.demo.service.Impl;

import com.example.demo.service.ISensitiveWordService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 违禁词检查服务 - DFA 算法实现 + Redis 持久化
 */
@Service
public class SensitiveWordServiceImpl implements ISensitiveWordService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordServiceImpl.class);

    /**
     * Redis 敏感词 Key
     */
    private static final String SENSITIVE_WORDS_REDIS_KEY = "sensitive:words";

    @Resource
    private RedisTemplate<String, Object> redisTemplate;


    /**
     * DFA 字典树根节点
     */
    @SuppressWarnings("unchecked")
    private final Map<String, Object> sensitiveWordMap = new ConcurrentHashMap<>();

    /**
     * 拼音映射表（拼音 -> 原词）
     */
    private final Map<String, Set<String>> pinyinWordMap = new ConcurrentHashMap<>();

    /**
     * 拼音输出格式
     */
    private final HanyuPinyinOutputFormat pinyinFormat;

    /**
     * 违禁词文件路径（默认）
     */
    private static final String DEFAULT_SENSITIVE_WORDS_FILE = "违禁词大全.txt";

    public SensitiveWordServiceImpl() {
        pinyinFormat = new HanyuPinyinOutputFormat();
        pinyinFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        pinyinFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        pinyinFormat.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    @PostConstruct
    public void init() {
        // 1. 优先从 Redis 加载
        Set<Object> redisWords = redisTemplate.opsForSet().members(SENSITIVE_WORDS_REDIS_KEY);
        if (redisWords != null && !redisWords.isEmpty()) {
            for (Object word : redisWords) {
                if (word != null) {
                    addSensitiveWordToMemory(word.toString());
                }
            }
            log.info("从 Redis 加载 {} 个敏感词", redisWords.size());
        } else {
            // 2. Redis 为空时从文件加载并初始化 Redis
            try {
                loadSensitiveWordsFromFile(DEFAULT_SENSITIVE_WORDS_FILE);
                // 初始化 Redis
                syncToRedis();
                log.info("违禁词库初始化完成 (从文件加载)");
            } catch (Exception e) {
                log.warn("未找到默认违禁词文件，请手动加载");
            }
        }
    }

    @Override
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String cleanedText = cleanAndNormalize(text);
        return checkWithDFA(cleanedText) || checkWithPinyin(cleanedText);
    }

    @Override
    public Set<String> findSensitiveWords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        String cleanedText = cleanAndNormalize(text);

        // DFA 匹配
        result.addAll(findWithDFA(cleanedText));

        // 拼音匹配
        result.addAll(findWithPinyin(cleanedText));

        return result;
    }

    @Override
    public String filterSensitiveWords(String text, char replacement) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Set<String> sensitiveWords = findSensitiveWords(text);
        if (sensitiveWords.isEmpty()) {
            return text;
        }

        String result = text;
        for (String word : sensitiveWords) {
            String replacementStr = String.valueOf(replacement).repeat(word.length());
            // 替换原文中的违禁词（考虑大小写和全角半角）
            result = replaceIgnoreCase(result, word, replacementStr);
        }
        return result;
    }

    @Override
    public String highlightSensitiveWords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Set<String> sensitiveWords = findSensitiveWords(text);
        if (sensitiveWords.isEmpty()) {
            return text;
        }

        String result = text;
        for (String word : sensitiveWords) {
            result = replaceIgnoreCase(result, word, "【" + word + "】");
        }
        return result;
    }

    @Override
    public void loadSensitiveWords(String filePath) {
        try {
            // 尝试从类路径加载
            java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath);
            if (inputStream == null) {
                // 如果类路径找不到，尝试从项目根目录加载
                java.io.File file = new java.io.File(filePath);
                if (file.exists()) {
                    inputStream = new java.io.FileInputStream(file);
                } else {
                    log.warn("违禁词文件不存在：{}", filePath);
                    return;
                }
            }
            
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(inputStream, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        // 处理格式："敏感词|标记" -> 只取敏感词部分
                        String word = line.contains("|") ? line.split("\\|")[0].trim() : line;
                        if (!word.isEmpty()) {
                            addSensitiveWord(word);
                        }
                    }
                }
                log.info("从文件 {} 加载违禁词完成", filePath);
            }
        } catch (IOException e) {
            log.error("加载违禁词文件失败：{}", filePath, e);
        }
    }

    @Override
    public void addSensitiveWord(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }

        String normalized = cleanAndNormalize(word);

        // 添加到 DFA 树
        addToDFATree(normalized);

        // 添加到拼音映射表
        String pinyin = convertToPinyin(normalized);
        if (pinyin != null && !pinyin.equals(normalized)) {
            pinyinWordMap.computeIfAbsent(pinyin, k -> ConcurrentHashMap.newKeySet()).add(normalized);
        }

        // 同步到 Redis
        redisTemplate.opsForSet().add(SENSITIVE_WORDS_REDIS_KEY, word);
    }

    @Override
    public void removeSensitiveWord(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }

        String normalized = cleanAndNormalize(word);

        // 从 DFA 树中删除（需要遍历到末尾节点标记为非结尾）
        removeFromDFATree(normalized);

        // 从拼音映射表删除
        String pinyin = convertToPinyin(normalized);
        if (pinyin != null) {
            Set<String> words = pinyinWordMap.get(pinyin);
            if (words != null) {
                words.remove(normalized);
                if (words.isEmpty()) {
                    pinyinWordMap.remove(pinyin);
                }
            }
        }

        // 从 Redis 删除
        redisTemplate.opsForSet().remove(SENSITIVE_WORDS_REDIS_KEY, word);
    }

    @Override
    public List<String> getAllSensitiveWords() {
        List<String> result = new ArrayList<>();
        collectWordsFromDFATree(sensitiveWordMap, "", result);
        return result;
    }

    /**
     * 从 Redis 全量同步到内存
     */
    public void syncFromRedis() {
        Set<Object> redisWords = redisTemplate.opsForSet().members(SENSITIVE_WORDS_REDIS_KEY);
        if (redisWords == null || redisWords.isEmpty()) {
            log.warn("Redis 中没有敏感词数据");
            return;
        }

        // 清空内存
        sensitiveWordMap.clear();
        pinyinWordMap.clear();

        // 重新加载
        for (Object word : redisWords) {
            if (word != null) {
                addSensitiveWordToMemory(word.toString());
            }
        }
        log.info("从 Redis 同步 {} 个敏感词完成", redisWords.size());
    }

    /**
     * 从文件加载（仅内部使用）
     */
    private void loadSensitiveWordsFromFile(String filePath) {
        try {
            java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath);
            if (inputStream == null) {
                java.io.File file = new java.io.File(filePath);
                if (file.exists()) {
                    inputStream = new java.io.FileInputStream(file);
                } else {
                    throw new IOException("文件不存在：" + filePath);
                }
            }

            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(inputStream, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        String word = line.contains("|") ? line.split("\\|")[0].trim() : line;
                        if (!word.isEmpty()) {
                            addSensitiveWordToMemory(word);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("加载违禁词文件失败：{}", filePath, e);
        }
    }

    /**
     * 同步所有敏感词到 Redis
     */
    private void syncToRedis() {
        List<String> words = getAllSensitiveWords();
        if (!words.isEmpty()) {
            redisTemplate.opsForSet().add(SENSITIVE_WORDS_REDIS_KEY, words.toArray());
            log.info("同步 {} 个敏感词到 Redis", words.size());
        }
    }

    /**
     * 添加敏感词到内存（不写 Redis）
     */
    private void addSensitiveWordToMemory(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }

        String normalized = cleanAndNormalize(word);

        // 添加到 DFA 树
        addToDFATree(normalized);

        // 添加到拼音映射表
        String pinyin = convertToPinyin(normalized);
        if (pinyin != null && !pinyin.equals(normalized)) {
            pinyinWordMap.computeIfAbsent(pinyin, k -> ConcurrentHashMap.newKeySet()).add(normalized);
        }
    }

    // ========================================
    // DFA 算法核心实现
    // ========================================

    /**
     * 将词添加到 DFA 字典树
     */
    @SuppressWarnings("unchecked")
    private void addToDFATree(String word) {
        Map<String, Object> currentMap = sensitiveWordMap;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            String key = String.valueOf(c);
            Object next = currentMap.get(key);

            if (next == null) {
                Map<String, Object> newMap = new ConcurrentHashMap<>();
                newMap.put("isEnd", i == word.length() - 1);
                currentMap.put(key, newMap);
                currentMap = newMap;
            } else {
                currentMap = (Map<String, Object>) next;
                if (i == word.length() - 1) {
                    currentMap.put("isEnd", true);
                }
            }
        }
    }

    /**
     * 从 DFA 树删除词
     */
    @SuppressWarnings("unchecked")
    private void removeFromDFATree(String word) {
        Map<String, Object> currentMap = sensitiveWordMap;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Object next = currentMap.get(String.valueOf(c));
            if (next == null) {
                return;
            }
            currentMap = (Map<String, Object>) next;
        }
        currentMap.put("isEnd", false);
    }

    /**
     * DFA 检查文本是否包含违禁词
     */
    @SuppressWarnings("unchecked")
    private boolean checkWithDFA(String text) {
        Map<String, Object> currentMap = sensitiveWordMap;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Object next = currentMap.get(String.valueOf(c));

            if (next == null) {
                currentMap = sensitiveWordMap;
                continue;
            }

            currentMap = (Map<String, Object>) next;
            if (Boolean.TRUE.equals(currentMap.get("isEnd"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * DFA 查找所有违禁词
     */
    @SuppressWarnings("unchecked")
    private Set<String> findWithDFA(String text) {
        Set<String> result = new HashSet<>();
        Map<String, Object> currentMap = sensitiveWordMap;
        StringBuilder wordBuilder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Object next = currentMap.get(String.valueOf(c));

            if (next == null) {
                currentMap = sensitiveWordMap;
                wordBuilder.setLength(0);
                continue;
            }

            wordBuilder.append(c);
            currentMap = (Map<String, Object>) next;

            if (Boolean.TRUE.equals(currentMap.get("isEnd"))) {
                result.add(wordBuilder.toString());
                wordBuilder.setLength(0);
                currentMap = sensitiveWordMap;
            }
        }
        return result;
    }

    /**
     * 拼音匹配检查
     */
    private boolean checkWithPinyin(String text) {
        if (!shouldUsePinyinMatch(text)) {
            return false;
        }
        String pinyin = convertToPinyin(text);
        if (pinyin == null) {
            return false;
        }
        return !findWithPinyin(text).isEmpty();
    }

    /**
     * 拼音匹配查找
     */
    private Set<String> findWithPinyin(String text) {
        Set<String> result = new HashSet<>();
        if (!shouldUsePinyinMatch(text)) {
            return result;
        }
        String pinyin = convertToPinyin(text);
        if (pinyin == null) {
            return result;
        }

        for (Map.Entry<String, Set<String>> entry : pinyinWordMap.entrySet()) {
            if (pinyin.contains(entry.getKey())) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    /**
     * 拼音匹配仅用于纯字母/数字输入，避免中文文本转拼音后的同音误判（如“编译器”误判“一期”）。
     */
    private boolean shouldUsePinyinMatch(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        boolean hasAsciiLetterOrDigit = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FA5) {
                return false;
            }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                hasAsciiLetterOrDigit = true;
            }
        }
        return hasAsciiLetterOrDigit;
    }

    // ========================================
    // 文本处理工具方法
    // ========================================

    /**
     * 清洗和归一化文本
     * 1. 去掉空格和特殊符号
     * 2. 全角转半角
     * 3. 转小写
     */
    private String cleanAndNormalize(String text) {
        if (text == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 去掉空格和特殊符号（保留中文、英文、数字）
            if (!isKeepChar(c)) {
                continue;
            }

            // 全角转半角
            c = fullWidthToHalfWidth(c);

            // 转小写
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            }

            result.append(c);
        }
        return result.toString();
    }

    /**
     * 判断字符是否需要保留（中文、英文、数字）
     */
    private boolean isKeepChar(char c) {
        // 中文
        if (c >= 0x4E00 && c <= 0x9FA5) {
            return true;
        }
        // 英文
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
            return true;
        }
        // 数字
        if (c >= '0' && c <= '9') {
            return true;
        }
        return false;
    }

    /**
     * 全角转半角
     */
    private char fullWidthToHalfWidth(char c) {
        // 全角空格
        if (c == 12288) {
            return ' ';
        }
        // 其他全角字符（！到～）
        if (c >= 65281 && c <= 65374) {
            return (char) (c - 65248);
        }
        return c;
    }

    /**
     * 将文本转换为拼音
     */
    private String convertToPinyin(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 中文转拼音
            if (c >= 0x4E00 && c <= 0x9FA5) {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, pinyinFormat);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        result.append(pinyinArray[0]);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    result.append(c);
                }
            } else {
                // 英文数字直接保留
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 忽略大小写替换
     */
    private String replaceIgnoreCase(String text, String target, String replacement) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            boolean found = true;
            for (int j = 0; j < target.length() && i + j < text.length(); j++) {
                char tc = fullWidthToHalfWidth(text.charAt(i + j));
                char wc = fullWidthToHalfWidth(target.charAt(j));
                if (Character.toLowerCase(tc) != Character.toLowerCase(wc)) {
                    found = false;
                    break;
                }
            }
            if (found && target.length() <= text.length() - i) {
                result.append(replacement);
                i += target.length();
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 从 DFA 树收集所有词
     */
    @SuppressWarnings("unchecked")
    private void collectWordsFromDFATree(Map<String, Object> map, String prefix, List<String> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if ("isEnd".equals(entry.getKey())) {
                continue;
            }

            String word = prefix + entry.getKey();
            Map<String, Object> nextMap = (Map<String, Object>) entry.getValue();

            if (Boolean.TRUE.equals(nextMap.get("isEnd"))) {
                result.add(word);
            }

            collectWordsFromDFATree(nextMap, word, result);
        }
    }
}
