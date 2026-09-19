package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityResult {
    private Long seq;
    private Long jobSeq;
    private Long sourceAbstractSeq;
    private Long targetAbstractSeq;
    private String targetSubmissionNo;
    private String targetTitle;
    private Double overallSimilarity;
    private Double titleSimilarity;
    private Double objectiveSimilarity;
    private Double methodsSimilarity;
    private Double resultsSimilarity;
    private Double conclusionsSimilarity;
    private String highestSection;
    private Double highestSimilarity;
    private String modelName;
    private String scoringVersion;
    private String sourceContentHash;
    private String targetContentHash;
    private LocalDateTime analyzedAt;
}
