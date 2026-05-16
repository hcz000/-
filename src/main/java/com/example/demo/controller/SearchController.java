package com.example.demo.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.search.SearchResult;
import com.example.demo.service.SearchService;
import com.example.demo.util.PageParamUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 搜索控制器
 */
@Api(tags = "搜索", description = "提供全局搜索、并行搜索等功能")
@RestController
@RequestMapping("/search")
public class SearchController {

    @Resource
    private SearchService searchService;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private Executor virtualThreadExecutor;

    /**
     * 同步搜索 - 同时查询帖子、星球、用户
     */
    @GetMapping("/all")
    public SaResult searchAll(@RequestParam String keyword,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        List<SearchResult> result = searchService.searchAll(keyword, p, s);
        return SaResult.ok().setData(result);
    }

    /**
     * 并行搜索 - 使用虚拟线程并行查询帖子、星球、用户
     * 使用注入的 virtualThreadExecutor 自动传播链路追踪上下文
     */
    @GetMapping("/async")
    public SaResult searchAllAsync(@RequestParam String keyword,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        CompletableFuture<List<SearchResult>> postsFuture = CompletableFuture.supplyAsync(
                () -> searchService.searchByBizType("post", keyword, p, s), virtualThreadExecutor);
        CompletableFuture<List<SearchResult>> planetsFuture = CompletableFuture.supplyAsync(
                () -> searchService.searchByBizType("planet", keyword, p, s), virtualThreadExecutor);
        CompletableFuture<List<SearchResult>> usersFuture = CompletableFuture.supplyAsync(
                () -> searchService.searchByBizType("user", keyword, p, s), virtualThreadExecutor);

        CompletableFuture.allOf(postsFuture, planetsFuture, usersFuture).join();

        List<SearchResult> posts = postsFuture.join();
        List<SearchResult> planets = planetsFuture.join();
        List<SearchResult> users = usersFuture.join();

        Map<String, Object> result = new HashMap<>();
        result.put("posts", posts);
        result.put("planets", planets);
        result.put("users", users);

        return SaResult.ok().setData(result);
    }

    /**
     * 按业务类型搜索
     */
    @GetMapping("/biz/{bizType}")
    public SaResult searchByBizType(@PathVariable String bizType,
                                    @RequestParam String keyword,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        List<SearchResult> result = searchService.searchByBizType(bizType, keyword, p, s);
        return SaResult.ok().setData(result);
    }
}
