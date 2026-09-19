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
public class AbstractSimilarityReviewCandidate {
    private Long abstractSeq;
    private String submissionNo;
    private String title;
    private String abstractStatus;
    private Long targetAbstractSeq;
    private String targetSubmissionNo;
    private String targetTitle;
    private Double overallSimilarity;
    private String highestSection;
    private Double highestSimilarity;
    private LocalDateTime analyzedAt;
    private Boolean stale;
    private Long reviewSeq;
    private String reviewStatus;
    private String reviewOpinion;
    private Boolean rejectionRecommended;
    private Long handledByAdminSeq;
    private String handledByAdminName;
    private LocalDateTime handledAt;
    private LocalDateTime updatedAt;
}
