package com.example.cloud.user.service;

import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.user.dto.FriendSummary;
import com.example.cloud.user.entity.FriendRelation;
import com.example.cloud.user.entity.FriendRequest;
import com.example.cloud.user.entity.User;
import com.example.cloud.user.enums.FriendRequestStatus;
import com.example.cloud.user.repository.FriendRelationRepository;
import com.example.cloud.user.repository.FriendRequestRepository;
import com.example.cloud.user.repository.UserRepository;
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
    private NotificationPublisher notificationPublisher;

    @Transactional(rollbackFor = Exception.class)
    public void sendFriendRequest(Long requesterId, Long targetUserId, String message) {
        if (targetUserId == null) throw new BusinessException("目标用户不能为空");
        if (requesterId.equals(targetUserId)) throw new BusinessException("不能添加自己为好友");
        ensureUserExists(targetUserId);
        if (areFriends(requesterId, targetUserId)) throw new BusinessException("已是好友，无需重复添加");
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

    @Transactional(rollbackFor = Exception.class)
    public void respondFriendRequest(Long requestId, Long operatorId, boolean accept) {
        log.info("[friend.respond] requestId={}, operatorId={}, accept={}", requestId, operatorId, accept);
        FriendRequest request = friendRequestRepository.findById(requestId).orElse(null);
        if (request == null) throw new BusinessException("好友申请不存在");
        if (!operatorId.equals(request.getTargetId())) throw new BusinessException("无权处理该好友申请");
        if (!request.getStatus().equals(FriendRequestStatus.PENDING.getCode())) {
            throw new BusinessException("该申请已处理");
        }
        LocalDateTime now = LocalDateTime.now();
        request.setStatus(accept ? FriendRequestStatus.ACCEPTED.getCode() : FriendRequestStatus.REJECTED.getCode());
        request.setUpdateTime(now);
        friendRequestRepository.save(request);
        if (accept) {
            createRelation(request.getRequesterId(), request.getTargetId(), now);
            notificationPublisher.notifyFriendRequestAccepted(
                    request.getTargetId(), request.getRequesterId(), requestId);
        } else {
            notificationPublisher.notifyFriendRequestRejected(
                    request.getTargetId(), request.getRequesterId(), requestId);
        }
        log.info("[friend.respond] DONE accept={}, requestId={}, requester={}, target={}",
                accept, requestId, request.getRequesterId(), request.getTargetId());
    }

    public List<FriendRequest> listPendingRequests(Long userId) {
        return friendRequestRepository.findByTargetIdAndStatus(userId, FriendRequestStatus.PENDING.getCode());
    }

    public List<FriendSummary> listFriends(Long userId) {
        if (userId == null) return new ArrayList<>();
        List<FriendRelation> relations = friendRelationRepository.findByUserIdInRelation(userId);
        if (relations == null || relations.isEmpty()) return new ArrayList<>();
        Set<Long> friendIds = relations.stream()
                .map(relation -> userId.equals(relation.getUserAId()) ? relation.getUserBId() : relation.getUserAId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (friendIds.isEmpty()) return new ArrayList<>();
        List<User> users = userRepository.findAllById(friendIds);
        if (users == null || users.isEmpty()) return new ArrayList<>();
        return users.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public boolean areFriends(Long userId, Long friendId) {
        if (userId == null || friendId == null) return false;
        Long[] pair = normalizePair(userId, friendId);
        return friendRelationRepository.existsByUserPair(pair[0], pair[1]);
    }

    public void ensureFriendship(Long userId, Long friendId) {
        if (!areFriends(userId, friendId)) throw new BusinessException("双方不是好友，无法执行该操作");
    }

    @Transactional(rollbackFor = Exception.class)
    protected void createRelation(Long userId, Long friendId, LocalDateTime now) {
        Long[] pair = normalizePair(userId, friendId);
        Optional<FriendRelation> existing = friendRelationRepository.findByUserPair(pair[0], pair[1]);
        if (existing.isPresent()) {
            FriendRelation relation = existing.get();
            if (!Boolean.TRUE.equals(relation.getDeleted())) return;
            relation.setDeleted(false);
            relation.setUpdateTime(now);
            friendRelationRepository.save(relation);
            return;
        }
        FriendRelation relation = new FriendRelation();
        relation.setUserAId(pair[0]);
        relation.setUserBId(pair[1]);
        relation.setCreateTime(now);
        relation.setUpdateTime(now);
        friendRelationRepository.save(relation);
    }

    private Long[] normalizePair(Long userId, Long friendId) {
        return userId.compareTo(friendId) <= 0 ? new Long[]{userId, friendId} : new Long[]{friendId, userId};
    }

    private void ensureUserExists(Long userId) {
        if (userRepository.findById(userId).isEmpty()) throw new BusinessException("用户不存在");
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
        if (!StringUtils.hasText(text)) return null;
        String trimmed = text.trim();
        return trimmed.length() <= MAX_REQUEST_MESSAGE ? trimmed : trimmed.substring(0, MAX_REQUEST_MESSAGE);
    }
}
