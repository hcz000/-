package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.post.dto.SearchResult;
import com.example.cloud.post.service.SearchService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    @Resource
    private SearchService searchService;

    @GetMapping("/all")
    public SaResult searchAll(@RequestParam String keyword,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        List<SearchResult> result = searchService.searchAll(keyword, p, s);
        return SaResult.ok().setData(result);
    }

    @GetMapping("/{bizType}")
    public SaResult searchByBiz(@PathVariable String bizType,
                                @RequestParam String keyword,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        return SaResult.ok().setData(searchService.searchByBizType(bizType, keyword, p, s));
    }
}
