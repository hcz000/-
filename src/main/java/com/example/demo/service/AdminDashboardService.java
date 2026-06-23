package com.example.demo.service;

import com.example.demo.repository.PlanetRepository;
import com.example.demo.repository.PostingsRepository;
import com.example.demo.repository.PrimaryCommentRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {

    @Resource
    private UserRepository userRepository;
    @Resource
    private PostingsRepository postingsRepository;
    @Resource
    private PlanetRepository planetRepository;
    @Resource
    private PrimaryCommentRepository primaryCommentRepository;
    @Resource
    private ReportRepository reportRepository;

    public Map<String, Object> overview() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalPosts", postingsRepository.count());
        stats.put("totalPlanets", planetRepository.count());
        stats.put("totalComments", primaryCommentRepository.count());
        stats.put("totalReports", reportRepository.count());
        return stats;
    }
}
