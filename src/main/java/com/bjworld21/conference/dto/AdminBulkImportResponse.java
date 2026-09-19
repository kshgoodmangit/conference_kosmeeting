package com.bjworld21.conference.dto;

import java.util.List;

public record AdminBulkImportResponse(
        int totalCount,
        int successCount,
        int failureCount,
        List<RowResult> results
) {
    public record RowResult(
            int rowNumber,
            String adminId,
            boolean success,
            String message
    ) {
    }
}
