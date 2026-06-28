package com.example.cloud.user.service;

import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.user.entity.ChatMessage;
import com.example.cloud.user.repository.ChatMessageRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ChatService {

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Resource
    private FriendService friendService;

    @Transactional(rollbackFor = Exception.class)
    public ChatMessage sendMessage(Long senderId, Long receiverId, String content) {
        if (receiverId == null) throw new BusinessException("好友 ID 不能为空");
        if (!StringUtils.hasText(content)) throw new BusinessException("消息内容不能为空");
        friendService.ensureFriendship(senderId, receiverId);

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId(senderId, receiverId));
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content.trim());
        message.setReadFlag(false);
        message.setCreateTime(LocalDateTime.now());
        chatMessageRepository.save(message);
        return message;
    }

    public Page<ChatMessage> listMessages(Long userId, Long friendId, int pageNum, int pageSize) {
        friendService.ensureFriendship(userId, friendId);
        Specification<ChatMessage> spec = (root, query, cb) ->
                cb.equal(root.get("conversationId"), conversationId(userId, friendId));
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.ASC, "createTime"));
        return chatMessageRepository.findAll(spec, pageable);
    }

    private String conversationId(Long userId, Long friendId) {
        return (userId < friendId) ? userId + ":" + friendId : friendId + ":" + userId;
    }
}
