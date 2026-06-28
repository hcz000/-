package com.example.cloud.user.service;

import com.example.cloud.user.repository.UserRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台首页统计（user-svc 域内）。
 * <p>
 * post / planet / comment / report 等统计在 post-svc 的 AdminDashboardService；
 * 完整 dashboard 应由前端聚合或网关 BFF 调用两个服务后合并。
 */
@Service
public class AdminDashboardService {

    @Resource
    private UserRepository userRepository;

    public Map<String, Object> overview() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        return stats;
    }
}
