package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractReviewReviewerResponse {
    private Long assignmentSeq;
    private Long reviewerSeq;
    private String reviewerName;
    private String affiliation;
    private String department;
    private String assignmentStatus;
    private LocalDateTime dueAt;
    private Long reviewSeq;
    private String reviewStatus;
    private String recommendation;
    private String overallComment;
    private String confidentialComment;
    private LocalDateTime submittedAt;
    private Double averageScore;
    private List<AbstractReviewScoreResponse> scores;
}
