package com.example.cloud.post.service;

import com.example.cloud.post.repository.PlanetRepository;
import com.example.cloud.post.repository.PostingsRepository;
import com.example.cloud.post.repository.PrimaryCommentRepository;
import com.example.cloud.post.repository.ReportRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AdminDashboardService {

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private PlanetRepository planetRepository;

    @Resource
    private PrimaryCommentRepository primaryCommentRepository;

    @Resource
    private ReportRepository reportRepository;

    /**
     * post-svc 域内的统计概览。
     * 用户统计在 user-svc 域的 AdminUserController 提供。
     */
    public Map<String, Object> overview() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPosts", postingsRepository.count());
        stats.put("totalPlanets", planetRepository.count());
        stats.put("totalComments", primaryCommentRepository.count());
        stats.put("totalReports", reportRepository.count());
        return stats;
    }
}
