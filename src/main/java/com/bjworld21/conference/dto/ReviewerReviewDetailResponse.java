package com.bjworld21.conference.dto;

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
public class ReviewerReviewDetailResponse {
    private Long assignmentSeq;
    private String assignmentStatus;
    private LocalDateTime dueAt;
    private Boolean showAuthorInformation;
    private AbstractSubmissionResponse abstractSubmission;
    private Long reviewSeq;
    private String reviewStatus;
    private String recommendation;
    private String overallComment;
    private String confidentialComment;
    private LocalDateTime submittedAt;
    private List<ReviewerReviewEvaluationItemResponse> evaluationItems;
}
