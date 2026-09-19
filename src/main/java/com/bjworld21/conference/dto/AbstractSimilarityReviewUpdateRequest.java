package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityReviewUpdateRequest {
    private String status;
    private String reviewOpinion;
    private Boolean rejectionRecommended;
}
