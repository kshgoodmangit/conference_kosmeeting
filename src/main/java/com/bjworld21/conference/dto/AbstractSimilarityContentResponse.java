package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityContentResponse {
    private Long abstractSeq;
    private String submissionNo;
    private String title;
    private String objectiveText;
    private String methodsText;
    private String resultsText;
    private String conclusionsText;
}
