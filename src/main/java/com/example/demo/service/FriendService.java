package com.example.demo.service;

import com.example.demo.entity.FriendRelation;
import com.example.demo.entity.FriendRequest;
import com.example.demo.entity.TNotification;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.FriendSummary;
import com.example.demo.enums.FriendRequestStatus;
import com.example.demo.enums.NotificationType;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.FriendRelationRepository;
import com.example.demo.repository.FriendRequestRepository;
import com.example.demo.repository.TNotificationRepository;
import com.example.demo.repository.UserRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FriendService {

    private static final int MAX_REQUEST_MESSAGE = 200;

    @Resource
    private FriendRequestRepository friendRequestRepository;
    @Resource
    private FriendRelationRepository friendRelationRepository;
    @Resource
    private UserRepository userRepository;
    @Resource
    private TNotificationRepository notificationRepository;


    @Transactional
    public void sendFriendRequest(Long requesterId, Long targetUserId, String message) {
        if (targetUserId == null) {
            throw new BusinessException("目标用户不能为空");
        }
        if (requesterId.equals(targetUserId)) {
            throw new BusinessException("不能添加自己为好友");
        }
        ensureUserExists(targetUserId);
        if (areFriends(requesterId, targetUserId)) {
            throw new BusinessException("已是好友，无需重复添加");
        }
        if (friendRequestRepository.existsPendingRequest(requesterId, targetUserId, FriendRequestStatus.PENDING.getCode())) {
            throw new BusinessException("已发送好友申请，请等待对方处理");
        }
        LocalDateTime now = LocalDateTime.now();
        FriendRequest request = new FriendRequest();
        request.setRequesterId(requesterId);
        request.setTargetId(targetUserId);
        request.setMessage(shorten(message));
        request.setStatus(FriendRequestStatus.PENDING.getCode());
        request.setCreateTime(now);
        request.setUpdateTime(now);
        friendRequestRepository.save(request);
    }

    @Transactional
    public void respondFriendRequest(Long requestId, Long operatorId, boolean accept) {
        log.info("[friend.respond] requestId={}, operatorId={}, accept={}", requestId, operatorId, accept);
        FriendRequest request = friendRequestRepository.findById(requestId).orElse(null);
        if (request == null) {
            log.warn("[friend.respond] request not found. requestId={}, operatorId={}", requestId, operatorId);
            throw new BusinessException("好友申请不存在");
        }
        log.info("[friend.respond] loaded request: id={}, requesterId={}, targetId={}, status={}",
                request.getId(), request.getRequesterId(), request.getTargetId(), request.getStatus());
        if (!operatorId.equals(request.getTargetId())) {
            log.warn("[friend.respond] forbidden. requestId={}, operatorId={}, targetId={}",
                    requestId, operatorId, request.getTargetId());
            throw new BusinessException("无权处理该好友申请");
        }
        if (!request.getStatus().equals(FriendRequestStatus.PENDING.getCode())) {
            log.warn("[friend.respond] already handled. requestId={}, status={}", requestId, request.getStatus());
            throw new BusinessException("该申请已处理");
        }
        LocalDateTime now = LocalDateTime.now();
        request.setStatus(accept ? FriendRequestStatus.ACCEPTED.getCode() : FriendRequestStatus.REJECTED.getCode());
        request.setUpdateTime(now);
        friendRequestRepository.save(request);
        if (accept) {
            createRelation(request.getRequesterId(), request.getTargetId(), now);
            TNotification notification = new TNotification();
            // 通知应该发给申请发起方（requester），而不是处理方（target）
            notification.setSenderId(request.getTargetId());
            notification.setRecipientId(request.getRequesterId());
            notification.setRelatedId(String.valueOf(requestId));
            notification.setType(NotificationType.FRIEND_REQUEST_ACCEPTED.getCode());
            notification.setContent("你的好友申请已通过");
            notification.setCreateTime(now);
            notification.setReadFlag(false);
            notificationRepository.save(notification);
        } else {
            TNotification notification = new TNotification();
            notification.setSenderId(request.getTargetId());
            notification.setRecipientId(request.getRequesterId());
            notification.setRelatedId(String.valueOf(requestId));
            notification.setType(NotificationType.FRIEND_REQUEST_REJECTED.getCode());
            notification.setContent("你的好友申请被拒绝");
            notification.setCreateTime(now);
            notification.setReadFlag(false);
            notificationRepository.save(notification);
        }
    }


    public List<FriendRequest> listPendingRequests(Long userId) {
        List<FriendRequest> requests = friendRequestRepository.findByTargetIdAndStatus(userId, FriendRequestStatus.PENDING.getCode());
        String idMap = requests.stream()
                .map(r -> "id=" + r.getId() + ",requesterId=" + r.getRequesterId() + ",targetId=" + r.getTargetId())
                .collect(Collectors.joining(" | "));
        log.info("[friend.pending] userId={}, count={}, mappings=[{}]", userId, requests.size(), idMap);
        return requests;
    }

    public List<FriendSummary> listFriends(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<FriendRelation> relations = friendRelationRepository.findByUserIdInRelation(userId);
        if (relations == null || relations.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> friendIds = relations.stream()
                .map(relation -> userId.equals(relation.getUserAId()) ? relation.getUserBId() : relation.getUserAId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (friendIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<User> users = userRepository.findAllById(friendIds);
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }
        return users.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public boolean areFriends(Long userId, Long friendId) {
        if (userId == null || friendId == null) {
            return false;
        }
        Long[] pair = normalizePair(userId, friendId);
        return friendRelationRepository.existsByUserPair(pair[0], pair[1]);
    }


    public void ensureFriendship(Long userId, Long friendId) {
        if (!areFriends(userId, friendId)) {
            throw new BusinessException("双方不是好友，无法执行该操作");
        }
    }


    @Transactional
    private void createRelation(Long userId, Long friendId, LocalDateTime now) {
        Long[] pair = normalizePair(userId, friendId);
        // 查是否有过好友关系（含已软删除的）
        Optional<FriendRelation> existing = friendRelationRepository.findByUserPair(pair[0], pair[1]);
        if (existing.isPresent()) {
            FriendRelation relation = existing.get();
            if (!Boolean.TRUE.equals(relation.getDeleted())) {
                return; // 已经是好友
            }
            // 恢复之前删除的好友关系
            relation.setDeleted(false);
            relation.setUpdateTime(now);
            friendRelationRepository.save(relation);
            return;
        }
        // 全新的好友关系
        FriendRelation relation = new FriendRelation();
        relation.setUserAId(pair[0]);
        relation.setUserBId(pair[1]);
        relation.setCreateTime(now);
        relation.setUpdateTime(now);
        friendRelationRepository.save(relation);
    }


    private Long[] normalizePair(Long userId, Long friendId) {
        if (userId.compareTo(friendId) <= 0) {
            return new Long[]{userId, friendId};
        }
        return new Long[]{friendId, userId};
    }


    private void ensureUserExists(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
    }


    private FriendSummary toSummary(User user) {
        FriendSummary summary = new FriendSummary();
        summary.setUserId(user.getId());
        summary.setUsername(user.getUsername());
        summary.setAvatar(user.getAvatar());
        summary.setEmail(user.getEmail());
        return summary;
    }


    private String shorten(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_REQUEST_MESSAGE) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_REQUEST_MESSAGE);
    }

}
