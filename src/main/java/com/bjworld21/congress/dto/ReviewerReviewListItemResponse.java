package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerReviewListItemResponse {
    private Long assignmentSeq;
    private Long abstractSeq;
    private String submissionNo;
    private String title;
    private String categoryName;
    private String presentationTypeName;
    private String assignmentStatus;
    private LocalDateTime dueAt;
    private LocalDateTime assignedAt;
    private Long reviewSeq;
    private String reviewStatus;
    private String recommendation;
    private LocalDateTime reviewUpdatedAt;
}
