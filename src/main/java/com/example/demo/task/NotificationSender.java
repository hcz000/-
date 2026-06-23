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
        send(senderId, recipientId, NotificationType.POST_LIKE, postId, null,
                "有人点赞了你的帖子：" + shorten(postTitle));
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
        send(senderId, recipientId, NotificationType.COMMENT_MENTION,
                postingsId, buildMentionLink(postingsId, commentId), content);
    }

    public void notifyPlanetNewPost(Long senderId, Long recipientId, Long planetId, Long postId,
                                    String planetName, String postTitle) {
        send(senderId, recipientId, NotificationType.PLANET_NEW_POST, postId, "/planets/" + planetId,
                planetName + " 有新帖子：" + shorten(postTitle));
    }

    public void notifyFriendRequestAccepted(Long senderId, Long recipientId, Long requestId) {
        send(senderId, recipientId, NotificationType.FRIEND_REQUEST_ACCEPTED, null,
                requestId == null ? null : String.valueOf(requestId), "你的好友申请已通过");
    }

    public void notifyFriendRequestRejected(Long senderId, Long recipientId, Long requestId) {
        send(senderId, recipientId, NotificationType.FRIEND_REQUEST_REJECTED, null,
                requestId == null ? null : String.valueOf(requestId), "你的好友申请被拒绝");
    }

    public void notifySystem(Long recipientId, Long postId, String relatedId, String content) {
        send(null, recipientId, NotificationType.SYSTEM, postId, relatedId, content);
    }

    private void send(Long senderId, Long recipientId, NotificationType type,
                      Long postId, String relatedId, String content) {
        if (recipientId == null || type == null) {
            return;
        }
        if (senderId != null && senderId.equals(recipientId)) {
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
            log.warn("send notification failed, recipientId={}, type={}", recipientId, type, e);
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
