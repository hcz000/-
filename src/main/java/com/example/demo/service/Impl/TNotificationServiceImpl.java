package com.example.demo.service.Impl;

import com.example.demo.config.CacheConfig;
import com.example.demo.entity.TNotification;
import com.example.demo.repository.TNotificationRepository;
import com.example.demo.service.ITNotificationService;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知表 服务实现类
 */
@Service
public class TNotificationServiceImpl implements ITNotificationService {

    @Resource
    private TNotificationRepository notificationRepository;
    @Resource
    private CacheManager cacheManager;

    @Override
    public Page<TNotification> pageNotifications(Long userId, int pageNum, int pageSize, Boolean readFlag) {
        Specification<TNotification> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("recipientId"), userId));
            if (readFlag != null) {
                predicate = cb.and(predicate, cb.equal(root.get("readFlag"), readFlag));
            }
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return notificationRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConfig.CACHE_UNREAD_COUNT, key = "#userId")
    public boolean markAsRead(Long userId, Long notificationId) {
        TNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || !userId.equals(notification.getRecipientId())) {
            return false;
        }
        notification.setReadFlag(true);
        notificationRepository.save(notification);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConfig.CACHE_UNREAD_COUNT, key = "#userId")
    public boolean deleteNotification(Long userId, Long notificationId) {
        TNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || !userId.equals(notification.getRecipientId())) {
            return false;
        }
        notificationRepository.delete(notification);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConfig.CACHE_UNREAD_COUNT, key = "#userId")
    public void clearNotifications(Long userId) {
        Specification<TNotification> spec = (root, query, cb) -> {
            Predicate predicate = cb.equal(root.get("recipientId"), userId);
            return predicate;
        };
        notificationRepository.deleteAll(notificationRepository.findAll(spec));
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_UNREAD_COUNT, key = "#userId")
    public int getUnreadCount(Long userId) {
        Specification<TNotification> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("recipientId"), userId));
            predicate = cb.and(predicate, cb.equal(root.get("readFlag"), false));
            return predicate;
        };
        return (int) notificationRepository.count(spec);
    }

    @Override
    public TNotification save(TNotification notification) {
        return notificationRepository.save(notification);
    }
}