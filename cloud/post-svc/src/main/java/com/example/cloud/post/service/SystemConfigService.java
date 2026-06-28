package com.example.cloud.post.service;

import com.example.cloud.post.entity.SystemConfig;
import com.example.cloud.post.repository.SystemConfigRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统配置服务（简化版：去掉 @Cacheable，每次查询 DB；规模大了再加缓存）。
 */
@Service
public class SystemConfigService {

    public static final String HOT_LIKE_WEIGHT = "hot.like.weight";
    public static final String HOT_REPLY_WEIGHT = "hot.reply.weight";
    public static final String HOT_HALF_LIFE_HOURS = "hot.half_life.hours";
    public static final String AUDIT_ENABLED = "audit.enabled";

    @Resource
    private SystemConfigRepository systemConfigRepository;

    public List<SystemConfig> listAll() {
        return systemConfigRepository.findAll();
    }

    public String getValue(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .filter(StringUtils::hasText)
                .orElse(defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(getValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getValue(key, String.valueOf(defaultValue)));
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemConfig upsert(String key, String value, String description) {
        if (!StringUtils.hasText(key) || value == null) {
            throw new IllegalArgumentException("config key and value are required");
        }
        SystemConfig config = systemConfigRepository.findByConfigKey(key).orElseGet(SystemConfig::new);
        config.setConfigKey(key.trim());
        config.setConfigValue(value);
        config.setDescription(description);
        return systemConfigRepository.save(config);
    }
}
