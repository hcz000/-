package com.example.demo.entity.search;

import lombok.Data;

/**
 * 搜索结果载体（MySQL 模糊查询）
 */
@Data
public class SearchResult {

    private Long bizId;
    private String bizType;
    private String title;
    private String content;
    private String username;
    private Long planetId;
    private String planetCategory;
}
