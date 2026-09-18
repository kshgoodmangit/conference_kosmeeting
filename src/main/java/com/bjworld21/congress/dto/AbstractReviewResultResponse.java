package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractReviewResultResponse {
    private Long abstractSeq;
    private String submissionNo;
    private String title;
    private Integer assignedCount;
    private Integer completedCount;
    private Double averageScore;
    private Map<String, Integer> recommendationCounts;
    private List<AbstractReviewEvaluationSummaryResponse> evaluationSummaries;
    private List<AbstractReviewReviewerResponse> reviews;
}
