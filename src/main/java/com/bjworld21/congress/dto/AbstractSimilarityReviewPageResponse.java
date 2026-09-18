package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityReviewPageResponse {
    private List<AbstractSimilarityReviewItemResponse> items;
    private int page;
    private int size;
    private int totalPages;
    private long totalCount;
    private long openCount;
    private long concernCount;
    private double overallThreshold;
    private double sectionThreshold;
    private double criticalOverallThreshold;
    private double criticalSectionThreshold;
}
