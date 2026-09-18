package com.bjworld21.congress.dto;

import java.util.List;

public record AbstractReviewAssignmentBulkImportResponse(
        int totalCount,
        int successCount,
        int skippedCount,
        int failureCount,
        List<RowResult> results
) {
    public record RowResult(
            int rowNumber,
            String submissionNo,
            String reviewerId,
            String status,
            String message
    ) {
    }
}
