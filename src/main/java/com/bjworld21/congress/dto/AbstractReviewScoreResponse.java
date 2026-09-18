package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractReviewScoreResponse {
    private Long reviewSeq;
    private Long evaluationItemSeq;
    private String itemName;
    private Integer sortOrder;
    private Integer score;
    private String itemComment;
}
