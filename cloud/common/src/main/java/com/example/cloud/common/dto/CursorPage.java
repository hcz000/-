package com.example.cloud.common.dto;

import java.util.List;

/**
 * 游标分页响应。
 *
 * @param records 当前页数据
 * @param hasMore 是否还有下一页
 */
public record CursorPage<T>(List<T> records, boolean hasMore) {

    public static <T> CursorPage<T> of(List<T> records, boolean hasMore) {
        return new CursorPage<>(records, hasMore);
    }
}
