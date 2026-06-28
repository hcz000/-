package com.example.cloud.post.service;

import com.example.cloud.post.dto.SearchResult;
import com.example.cloud.post.entity.Planet;
import com.example.cloud.post.entity.Postings;
import com.example.cloud.post.repository.PlanetRepository;
import com.example.cloud.post.repository.PostingsRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索服务。
 * <p>
 * 与单体相比的简化：去掉对 user 表的搜索（User 在 user-svc，
 * 跨服务搜索成本高，演示版只搜帖子和星球）。
 * 用户搜索如需支持，调用方可走 Feign 调 user-svc 的 /user/search。
 */
@Service
public class SearchService {

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private PlanetRepository planetRepository;

    public List<SearchResult> searchAll(String keyword, int pageNum, int pageSize) {
        if (!StringUtils.hasText(keyword)) return new ArrayList<>();
        int baseQuota = Math.max(pageSize / 2, 1);
        List<SearchResult> result = new ArrayList<>();
        result.addAll(searchByBizType("post", keyword, pageNum, baseQuota));
        result.addAll(searchByBizType("planet", keyword, pageNum, pageSize - baseQuota));
        return result.size() <= pageSize ? result : result.subList(0, pageSize);
    }

    public List<SearchResult> searchByBizType(String bizType, String keyword, int pageNum, int pageSize) {
        if (!StringUtils.hasText(keyword)) return new ArrayList<>();
        if ("post".equalsIgnoreCase(bizType)) return searchPosts(keyword, pageNum, pageSize);
        if ("planet".equalsIgnoreCase(bizType)) return searchPlanets(keyword, pageNum, pageSize);
        return new ArrayList<>();
    }

    private List<SearchResult> searchPosts(String keyword, int pageNum, int pageSize) {
        long offset = (long) (pageNum - 1) * pageSize;
        List<Postings> records = postingsRepository.searchByFullText(keyword, pageSize, offset);
        if (records == null || records.isEmpty()) return new ArrayList<>();
        List<SearchResult> results = new ArrayList<>(records.size());
        for (Postings post : records) {
            SearchResult r = new SearchResult();
            r.setBizId(post.getPostingsId());
            r.setBizType("post");
            r.setTitle(post.getTitle());
            r.setContent(post.getContent());
            r.setPlanetId(post.getPlanetId());
            results.add(r);
        }
        return results;
    }

    private List<SearchResult> searchPlanets(String keyword, int pageNum, int pageSize) {
        long offset = (long) (pageNum - 1) * pageSize;
        List<Planet> records = planetRepository.searchByFullText(keyword, pageSize, offset);
        if (records == null || records.isEmpty()) return new ArrayList<>();
        List<SearchResult> results = new ArrayList<>(records.size());
        for (Planet planet : records) {
            SearchResult r = new SearchResult();
            r.setBizId(planet.getPlanetId());
            r.setBizType("planet");
            r.setTitle(planet.getName());
            r.setContent(planet.getDescription());
            r.setPlanetCategory(planet.getCategory());
            results.add(r);
        }
        return results;
    }
}
