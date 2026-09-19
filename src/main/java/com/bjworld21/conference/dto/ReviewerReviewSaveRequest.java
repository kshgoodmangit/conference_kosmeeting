package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerReviewSaveRequest {
    private String recommendation;
    private String overallComment;
    private String confidentialComment;
    private List<ReviewerReviewScoreRequest> scores;
}
