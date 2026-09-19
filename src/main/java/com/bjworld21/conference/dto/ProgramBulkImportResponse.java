package com.bjworld21.conference.dto;

import java.util.List;

public record ProgramBulkImportResponse(
        int totalCount,
        int successCount,
        int skippedCount,
        int failureCount,
        List<RowResult> results
) {
    public record RowResult(
            int rowNumber,
            String title,
            String status,
            String message
    ) {
    }
}
