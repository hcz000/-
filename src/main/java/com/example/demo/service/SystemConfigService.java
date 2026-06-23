package com.example.demo.service;

import com.example.demo.config.CacheConfig;
import com.example.demo.entity.SystemConfig;
import com.example.demo.repository.SystemConfigRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Cacheable(value = CacheConfig.CACHE_SYSTEM_CONFIG, key = "#key")
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
        String value = getValue(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConfig.CACHE_SYSTEM_CONFIG, key = "#key")
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
