package com.example.cloud.post.dto;

import lombok.Data;

/**
 * 搜索结果载体（MySQL FullText 查询）。
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
