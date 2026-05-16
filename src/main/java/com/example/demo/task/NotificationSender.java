package com.example.demo.task;

import com.example.demo.enums.NotificationType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class NotificationSender {

    @Resource
    private RabbitTemplate rabbitTemplate;

    public void notifyPostLike(Long senderId, Long recipientId, Long postId, String postTitle) {
        String content = "有人点赞了你的帖子：" + shorten(postTitle);
        send(senderId, recipientId, NotificationType.POST_LIKE, postId, null, content);
    }

    public void notifyPostComment(Long senderId, Long recipientId, Long postId, Long commentId, String content) {
        send(senderId, recipientId, NotificationType.POST_COMMENT,
                postId, commentId == null ? null : String.valueOf(commentId), content);
    }

    public void notifyCommentReply(Long senderId, Long recipientId, Long postId, Long commentId, String content) {
        send(senderId, recipientId, NotificationType.COMMENT_REPLY,
                postId, commentId == null ? null : String.valueOf(commentId), content);
    }

    public void notifyCommentMention(Long senderId, Long recipientId, Long postingsId, Long commentId, String content) {
        send(senderId, recipientId, NotificationType.COMMENT_MENTION, postingsId, buildMentionLink(postingsId, commentId), content);
    }

    /**
     * 星球新帖子通知
     * @param senderId 发帖者ID
     * @param recipientId 接收者ID（星球成员）
     * @param planetId 星球ID
     * @param postId 帖子ID
     * @param planetName 星球名称
     * @param postTitle 帖子标题
     */
    public void notifyPlanetNewPost(Long senderId, Long recipientId, Long planetId, Long postId, String planetName, String postTitle) {
        String relatedId = "/planets/" + planetId;
        String content = planetName + " 有新帖子：" + shorten(postTitle);
        send(senderId, recipientId, NotificationType.PLANET_NEW_POST, postId, relatedId, content);
    }

    private void send(Long senderId, Long recipientId, NotificationType type, Long postId, String relatedId, String content) {
        if (senderId == null || recipientId == null) {
            return;
        }
        if (senderId.equals(recipientId)) {
            return;
        }
        NotificationMessage message = new NotificationMessage(
                senderId,
                recipientId,
                type.getCode(),
                postId,
                relatedId,
                shorten(content));
        try {
            rabbitTemplate.convertAndSend(CommentTopics.NOTIFICATION, message.serialize());
        } catch (Exception e) {
            log.warn("为接收者 {} 发送通知入队失败", recipientId, e);
        }
    }

    private String buildMentionLink(Long postingsId, Long commentId) {
        if (postingsId == null) {
            return commentId == null ? null : String.valueOf(commentId);
        }
        StringBuilder builder = new StringBuilder("/postings/").append(postingsId);
        if (commentId != null) {
            builder.append("#comment-").append(commentId);
        }
        return builder.toString();
    }

    private String shorten(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60);
    }
}
