package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.task.NotificationSender;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CommentMentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([^@\\s]+)");

    @Resource
    private UserRepository userRepository;
    @Resource
    private FriendService friendService;
    @Resource
    private NotificationSender notificationSender;



    public void handleMentions(String content, Long senderId, Long commentId, Long postingsId) {
        if (!StringUtils.hasText(content) || senderId == null) {
            return;
        }
        Set<String> candidateNames = extractNames(content);
        if (candidateNames.isEmpty()) {
            return;
        }
        Specification<User> spec = (root, query, cb) -> {
            Predicate predicate = root.get("username").in(candidateNames);
            return predicate;
        };
        List<User> users = userRepository.findAll(spec);
        if (CollectionUtils.isEmpty(users)) {
            return;
        }
        for (User user : users) {
            if (user.getId() == null || senderId.equals(user.getId())) {
                continue;
            }
            if (!friendService.areFriends(senderId, user.getId())) {
                continue;
            }
            notificationSender.notifyCommentMention(senderId, user.getId(), postingsId, commentId, content);
        }
    }

    private Set<String> extractNames(String content) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String name = cleanName(matcher.group(1));
            if (StringUtils.hasText(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private String cleanName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.replaceAll("[,，.。!！?？:：;；@]", "");
    }
}