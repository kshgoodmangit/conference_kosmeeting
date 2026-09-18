package com.bjworld21.congress.service;

public record AbstractSimilarityProgress(
        String phase,
        int progressPercent,
        String message,
        int processedCount,
        int totalCount,
        int abstractCount
) {
}
