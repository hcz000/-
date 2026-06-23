package com.example.demo.service;

import com.example.demo.entity.Planet;
import com.example.demo.entity.Postings;
import com.example.demo.repository.PlanetMemberRepository;
import com.example.demo.repository.PlanetRepository;
import com.example.demo.task.NotificationSender;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AdminPostDistributionService {

    @Resource
    private CandidatePoolService candidatePoolService;
    @Resource
    private HotRankService hotRankService;
    @Resource
    private PlanetRepository planetRepository;
    @Resource
    private PlanetMemberRepository planetMemberRepository;
    @Resource
    private NotificationSender notificationSender;

    public void syncDistribution(Postings post) {
        if (shouldDistribute(post)) {
            refreshDistribution(post);
        } else {
            removeDistribution(post);
        }
    }

    public void refreshDistribution(Postings post) {
        if (post == null || post.getPostingsId() == null) {
            return;
        }
        candidatePoolService.addToPools(post);
        hotRankService.refreshPost(post);
    }

    public void removeDistribution(Postings post) {
        if (post == null || post.getPostingsId() == null) {
            return;
        }
        candidatePoolService.removeFromPools(post.getPostingsId(), post.getType());
        hotRankService.remove(post.getPostingsId());
    }

    public void notifyAuditResult(Postings post, Integer oldAuditStatus, Integer auditStatus) {
        if (post == null || post.getUserId() == null || auditStatus == null) {
            return;
        }
        if (Objects.equals(oldAuditStatus, auditStatus)) {
            return;
        }
        if (auditStatus == 1) {
            notificationSender.notifySystem(
                    post.getUserId(),
                    post.getPostingsId(),
                    "/posts/" + post.getPostingsId(),
                    "你的帖子《" + shortenTitle(post.getTitle()) + "》已审核通过");
            notifyPlanetMembersAfterApproval(post);
        } else if (auditStatus == 2) {
            notificationSender.notifySystem(
                    post.getUserId(),
                    post.getPostingsId(),
                    null,
                    "你的帖子《" + shortenTitle(post.getTitle()) + "》审核未通过");
        }
    }

    private boolean shouldDistribute(Postings post) {
        return post != null
                && post.getPostingsId() != null
                && !Boolean.TRUE.equals(post.getDeleted())
                && post.getStatus() != null && post.getStatus() == 1
                && post.getAuditStatus() != null && post.getAuditStatus() == 1;
    }

    private void notifyPlanetMembersAfterApproval(Postings post) {
        if (post == null || post.getPlanetId() == null || post.getUserId() == null) {
            return;
        }
        Planet planet = planetRepository.findById(post.getPlanetId()).orElse(null);
        if (planet == null) {
            return;
        }
        List<Long> memberIds = planetMemberRepository.findUserIdsByPlanetId(post.getPlanetId());
        for (Long memberId : memberIds) {
            if (memberId == null || memberId.equals(post.getUserId())) {
                continue;
            }
            try {
                notificationSender.notifyPlanetNewPost(
                        post.getUserId(),
                        memberId,
                        post.getPlanetId(),
                        post.getPostingsId(),
                        planet.getName(),
                        post.getTitle());
            } catch (Exception e) {
                log.warn("notify planet member new post failed, postId={}, memberId={}",
                        post.getPostingsId(), memberId, e);
            }
        }
    }

    private String shortenTitle(String title) {
        if (title == null || title.isBlank()) {
            return "未命名";
        }
        String trimmed = title.trim();
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30);
    }
}
