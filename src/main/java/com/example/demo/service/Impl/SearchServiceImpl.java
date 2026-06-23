package com.example.demo.service.Impl;

import com.example.demo.entity.Planet;
import com.example.demo.entity.User;
import com.example.demo.entity.search.SearchResult;
import com.example.demo.repository.PlanetRepository;
import com.example.demo.repository.PostingsRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.SearchService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索服务实现类
 * 使用 MySQL 8 InnoDB FULLTEXT + ngram parser 搜索
 */
@Service
public class SearchServiceImpl implements SearchService {

    @Resource
    private PostingsRepository postingsRepository;

    @Resource
    private PlanetRepository planetRepository;

    @Resource
    private UserRepository userRepository;

    @Override
    public List<SearchResult> searchAll(String keyword, int pageNum, int pageSize) {
        int baseQuota = Math.max(pageSize / 3, 1);
        int remainder = Math.max(pageSize - baseQuota * 3, 0);

        List<SearchResult> result = new ArrayList<>();
        result.addAll(searchByBizType("post", keyword, pageNum, baseQuota + (remainder > 0 ? 1 : 0)));
        result.addAll(searchByBizType("planet", keyword, pageNum, baseQuota + (remainder > 1 ? 1 : 0)));
        result.addAll(searchByBizType("user", keyword, pageNum, baseQuota));
        if (result.size() <= pageSize) {
            return result;
        }
        return result.subList(0, pageSize);
    }

    @Override
    public List<SearchResult> searchByBizType(String bizType, String keyword, int pageNum, int pageSize) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }
        if ("post".equalsIgnoreCase(bizType)) {
            return searchPosts(keyword, pageNum, pageSize);
        }
        if ("planet".equalsIgnoreCase(bizType)) {
            return searchPlanets(keyword, pageNum, pageSize);
        }
        if ("user".equalsIgnoreCase(bizType)) {
            return searchUsers(keyword, pageNum, pageSize);
        }
        return new ArrayList<>();
    }

    private List<SearchResult> searchPosts(String keyword, int pageNum, int pageSize) {
        long offset = (pageNum - 1) * pageSize;
        List<Object[]> rows = postingsRepository.searchByFullTextWithPlanetCategory(keyword, pageSize, offset);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<SearchResult> resultList = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long postId = row[0] == null ? null : ((Number) row[0]).longValue();
            String title = (String) row[1];
            String content = (String) row[2];
            Long planetId = row[3] == null ? null : ((Number) row[3]).longValue();
            String category = (String) row[4];
            resultList.add(buildResult(postId, "post", title, content, null, planetId, category));
        }
        return resultList;
    }

    private List<SearchResult> searchPlanets(String keyword, int pageNum, int pageSize) {
        long offset = (pageNum - 1) * pageSize;
        List<Planet> records = planetRepository.searchByFullText(keyword, pageSize, offset);

        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        List<SearchResult> searchResults = new ArrayList<>(records.size());
        for (Planet planet : records) {
            searchResults.add(buildResult(planet.getPlanetId(), "planet", planet.getName(), planet.getDescription(), null, null, planet.getCategory()));
        }
        return searchResults;
    }

    private List<SearchResult> searchUsers(String keyword, int pageNum, int pageSize) {
        long offset = (pageNum - 1) * pageSize;
        List<User> records = userRepository.searchByFullText(keyword, pageSize, offset);

        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        List<SearchResult> searchResults = new ArrayList<>(records.size());
        for (User user : records) {
            searchResults.add(buildResult(user.getId(), "user", null, null, user.getUsername(), null, null));
        }
        return searchResults;
    }

    private SearchResult buildResult(Long bizId, String bizType, String title, String content, String username, Long planetId, String planetCategory) {
        SearchResult result = new SearchResult();
        result.setBizId(bizId);
        result.setBizType(bizType);
        result.setTitle(title);
        result.setContent(content);
        result.setUsername(username);
        result.setPlanetId(planetId);
        result.setPlanetCategory(planetCategory);
        return result;
    }
}
