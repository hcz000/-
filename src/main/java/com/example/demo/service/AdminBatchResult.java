package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;

public record AdminBatchResult(int successCount, int failureCount, List<Long> failedIds) {

    public static AdminBatchResult of(int successCount, List<Long> failedIds) {
        List<Long> failures = failedIds == null ? List.of() : new ArrayList<>(failedIds);
        return new AdminBatchResult(successCount, failures.size(), failures);
    }
}
