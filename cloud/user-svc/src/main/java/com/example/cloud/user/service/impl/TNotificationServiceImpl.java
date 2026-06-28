package com.example.cloud.user.service.impl;

import com.example.cloud.user.entity.TNotification;
import com.example.cloud.user.repository.TNotificationRepository;
import com.example.cloud.user.service.ITNotificationService;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知服务实现（简化版：去掉 @Cacheable）。
 */
@Service
public class TNotificationServiceImpl implements ITNotificationService {

    @Resource
    private TNotificationRepository notificationRepository;

    @Override
    public Page<TNotification> pageNotifications(Long userId, int pageNum, int pageSize, Boolean readFlag) {
        Specification<TNotification> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("recipientId"), userId);
            if (readFlag != null) p = cb.and(p, cb.equal(root.get("readFlag"), readFlag));
            return p;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return notificationRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsRead(Long userId, Long notificationId) {
        TNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || !userId.equals(notification.getRecipientId())) return false;
        notification.setReadFlag(true);
        notificationRepository.save(notification);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNotification(Long userId, Long notificationId) {
        TNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || !userId.equals(notification.getRecipientId())) return false;
        notificationRepository.delete(notification);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearNotifications(Long userId) {
        Specification<TNotification> spec = (root, query, cb) -> cb.equal(root.get("recipientId"), userId);
        notificationRepository.deleteAll(notificationRepository.findAll(spec));
    }

    @Override
    public int getUnreadCount(Long userId) {
        Specification<TNotification> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("recipientId"), userId);
            p = cb.and(p, cb.equal(root.get("readFlag"), false));
            return p;
        };
        return (int) notificationRepository.count(spec);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNotification save(TNotification notification) {
        return notificationRepository.save(notification);
    }
}
