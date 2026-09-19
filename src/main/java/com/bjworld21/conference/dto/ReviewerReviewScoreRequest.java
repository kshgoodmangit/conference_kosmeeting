package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerReviewScoreRequest {
    private Long evaluationItemSeq;
    private Integer score;
    private String itemComment;
}
