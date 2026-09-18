package com.bjworld21.congress.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityReview {
    private Long seq;
    private Long conferenceSeq;
    private Long abstractSeq;
    private String status;
    private String reviewOpinion;
    private Boolean rejectionRecommended;
    private Long handledByAdminSeq;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
