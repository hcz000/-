package com.example.cloud.common.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台批量操作结果。
 */
public record AdminBatchResult(int successCount, int failureCount, List<Long> failedIds) {

    public static AdminBatchResult of(int successCount, List<Long> failedIds) {
        List<Long> failures = failedIds == null ? List.of() : new ArrayList<>(failedIds);
        return new AdminBatchResult(successCount, failures.size(), failures);
    }
}
