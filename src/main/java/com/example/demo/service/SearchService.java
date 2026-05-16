package com.example.demo.service;


import com.example.demo.entity.search.SearchResult;

import java.util.List;

public interface SearchService {

    List<SearchResult> searchAll(String keyword, int pageNum, int pageSize);

    List<SearchResult> searchByBizType(String bizType, String keyword, int pageNum, int pageSize);
}
