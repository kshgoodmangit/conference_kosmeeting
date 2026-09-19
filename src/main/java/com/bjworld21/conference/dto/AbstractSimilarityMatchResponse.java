package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityMatchResponse {
    private Long abstractSeq;
    private String submissionNo;
    private String title;
    private Double overallSimilarity;
    private Double titleSimilarity;
    private Double objectiveSimilarity;
    private Double methodsSimilarity;
    private Double resultsSimilarity;
    private Double conclusionsSimilarity;
    private String highestSection;
    private Double highestSimilarity;
}
