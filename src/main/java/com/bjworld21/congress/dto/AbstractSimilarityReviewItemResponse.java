package com.bjworld21.congress.dto;

import com.bjworld21.congress.entity.AbstractSimilarityReviewCandidate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityReviewItemResponse {
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
    private String riskLevel;
    private Long reviewSeq;
    private String reviewStatus;
    private String reviewOpinion;
    private Boolean rejectionRecommended;
    private Long handledByAdminSeq;
    private String handledByAdminName;
    private LocalDateTime handledAt;
    private LocalDateTime updatedAt;

    public static AbstractSimilarityReviewItemResponse from(
            AbstractSimilarityReviewCandidate candidate,
            String riskLevel
    ) {
        return AbstractSimilarityReviewItemResponse.builder()
                .abstractSeq(candidate.getAbstractSeq())
                .submissionNo(candidate.getSubmissionNo())
                .title(candidate.getTitle())
                .abstractStatus(candidate.getAbstractStatus())
                .targetAbstractSeq(candidate.getTargetAbstractSeq())
                .targetSubmissionNo(candidate.getTargetSubmissionNo())
                .targetTitle(candidate.getTargetTitle())
                .overallSimilarity(candidate.getOverallSimilarity())
                .highestSection(candidate.getHighestSection())
                .highestSimilarity(candidate.getHighestSimilarity())
                .analyzedAt(candidate.getAnalyzedAt())
                .stale(Boolean.TRUE.equals(candidate.getStale()))
                .riskLevel(riskLevel)
                .reviewSeq(candidate.getReviewSeq())
                .reviewStatus(candidate.getReviewStatus() == null ? "PENDING" : candidate.getReviewStatus())
                .reviewOpinion(candidate.getReviewOpinion())
                .rejectionRecommended(Boolean.TRUE.equals(candidate.getRejectionRecommended()))
                .handledByAdminSeq(candidate.getHandledByAdminSeq())
                .handledByAdminName(candidate.getHandledByAdminName())
                .handledAt(candidate.getHandledAt())
                .updatedAt(candidate.getUpdatedAt())
                .build();
    }
}
