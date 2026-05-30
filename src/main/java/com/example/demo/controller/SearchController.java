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

@Api(tags = "搜索", description = "提供全局搜索和并行搜索能力")
@RestController
@RequestMapping("/search")
public class SearchController {

    @Resource
    private SearchService searchService;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private Executor virtualThreadExecutor;

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
     * 使用虚拟线程并发查询帖子、星球和用户三类结果。
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

        Map<String, Object> result = new HashMap<>();
        result.put("posts", postsFuture.join());
        result.put("planets", planetsFuture.join());
        result.put("users", usersFuture.join());

        return SaResult.ok().setData(result);
    }

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
