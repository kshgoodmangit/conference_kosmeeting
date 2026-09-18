package com.bjworld21.congress.dto;

import com.bjworld21.congress.entity.AbstractTitleSimilarityResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractTitleSimilarityMatchResponse {
    private Long abstractSeq;
    private String submissionNo;
    private String title;
    private Double similarityScore;
    private Double levenshteinSimilarity;
    private Double trigramSimilarity;
    private Double jaccardSimilarity;
    private Boolean exactMatch;

    public static AbstractTitleSimilarityMatchResponse from(AbstractTitleSimilarityResult result) {
        return AbstractTitleSimilarityMatchResponse.builder()
                .abstractSeq(result.getTargetAbstractSeq())
                .submissionNo(result.getTargetSubmissionNo())
                .title(result.getTargetTitle())
                .similarityScore(result.getSimilarityScore())
                .levenshteinSimilarity(result.getLevenshteinSimilarity())
                .trigramSimilarity(result.getTrigramSimilarity())
                .jaccardSimilarity(result.getJaccardSimilarity())
                .exactMatch(result.getExactMatch())
                .build();
    }
}
