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
public class AbstractReviewAssignmentResponse {
    private Long abstractSeq;
    private String submissionNo;
    private String title;
    private String abstractStatus;
    private Long categoryCode;
    private String categoryName;
    private LocalDateTime dueAt;
    private List<ReviewerAssignmentCandidateResponse> reviewers;
}
