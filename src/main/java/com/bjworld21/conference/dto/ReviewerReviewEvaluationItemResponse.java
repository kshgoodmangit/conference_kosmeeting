package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerReviewEvaluationItemResponse {
    private Long evaluationItemSeq;
    private String itemName;
    private String description;
    private Integer sortOrder;
    private String score1Guide;
    private String score2Guide;
    private String score3Guide;
    private String score4Guide;
    private String score5Guide;
    private String score6Guide;
    private Integer score;
    private String itemComment;
}
