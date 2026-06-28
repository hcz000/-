package com.example.cloud.user.service;

import com.example.cloud.common.api.notification.NotificationEvent;
import com.example.cloud.common.api.notification.NotificationType;
import com.example.cloud.user.entity.User;
import com.example.cloud.user.repository.UserRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评论 @ 提醒服务。
 * <p>
 * 在 user-svc 中实现，原因：
 * <ul>
 *   <li>用户名 → userId 查询在 user-svc 域</li>
 *   <li>「是否好友」校验在 user-svc 域（{@link FriendService}）</li>
 *   <li>触发通知发到 notification.events 由 user-svc 自己消费</li>
 * </ul>
 *
 * 触发路径：post-svc 创建评论后 publish CommentCreatedEvent 到 MQ，
 * user-svc 这边新增一个 listener 调用本服务。（事件结构未在本批迁移定义，
 * 留 TODO：在 commit 评论功能完整起来后接通。）
 */
@Slf4j
@Service
public class CommentMentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([^@\\s]+)");

    @Resource
    private UserRepository userRepository;

    @Resource
    private FriendService friendService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 解析评论内容里的 @用户名，给提及到的（且是好友的）用户发通知。
     */
    public void handleMentions(String content, Long senderId, Long commentId, Long postingsId) {
        if (!StringUtils.hasText(content) || senderId == null) return;
        Set<String> candidateNames = extractNames(content);
        if (candidateNames.isEmpty()) return;

        Specification<User> spec = (root, query, cb) -> {
            Predicate p = root.get("username").in(candidateNames);
            return p;
        };
        List<User> users = userRepository.findAll(spec);
        if (CollectionUtils.isEmpty(users)) return;

        for (User user : users) {
            if (user.getId() == null || senderId.equals(user.getId())) continue;
            if (!friendService.areFriends(senderId, user.getId())) continue;
            // 发到统一 notification.events 交换机，由 NotificationEventListener 落库 + WS 推送
            String relatedId = "/postings/" + postingsId
                    + (commentId != null ? "#comment-" + commentId : "");
            NotificationEvent event = new NotificationEvent(
                    senderId, user.getId(), NotificationType.COMMENT_MENTION.getCode(),
                    postingsId, relatedId, shorten(content));
            try {
                rabbitTemplate.convertAndSend(NotificationEvent.EXCHANGE,
                        "notification." + NotificationType.COMMENT_MENTION.name().toLowerCase(),
                        event);
            } catch (Exception e) {
                log.warn("[mention] send notification failed, senderId={}, recipient={}",
                        senderId, user.getId(), e);
            }
        }
    }

    private Set<String> extractNames(String content) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String name = cleanName(matcher.group(1));
            if (StringUtils.hasText(name)) result.add(name);
        }
        return result;
    }

    private String cleanName(String raw) {
        if (!StringUtils.hasText(raw)) return "";
        return raw.trim().replaceAll("[,，.。!！?？:：;；@]", "");
    }

    private String shorten(String content) {
        if (!StringUtils.hasText(content)) return "";
        String trimmed = content.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60);
    }
}
