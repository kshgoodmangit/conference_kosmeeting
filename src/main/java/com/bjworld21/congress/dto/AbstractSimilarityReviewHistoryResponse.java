package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityReviewHistoryResponse {
    private Long seq;
    private String previousStatus;
    private String status;
    private String reviewOpinion;
    private Boolean rejectionRecommended;
    private Long handledByAdminSeq;
    private String handledByAdminName;
    private LocalDateTime handledAt;
}
