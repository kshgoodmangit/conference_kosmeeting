package com.bjworld21.congress.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractTitleSimilarityResult {
    private Long seq;
    private Long checkSeq;
    private Long sourceAbstractSeq;
    private Long targetAbstractSeq;
    private String targetSubmissionNo;
    private String targetTitle;
    private Double similarityScore;
    private Double levenshteinSimilarity;
    private Double trigramSimilarity;
    private Double jaccardSimilarity;
    private Boolean exactMatch;
    private LocalDateTime checkedAt;
}
