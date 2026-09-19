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
public class AbstractSimilarityJob {
    private Long seq;
    private Long conferenceSeq;
    private String status;
    private String phase;
    private Integer progressPercent;
    private String message;
    private Integer processedCount;
    private Integer totalCount;
    private Integer abstractCount;
    private Integer updatedAbstractCount;
    private Integer generatedEmbeddingCount;
    private Integer similarityResultCount;
    private Integer titleWeight;
    private Integer objectiveWeight;
    private Integer methodsWeight;
    private Integer resultsWeight;
    private Integer conclusionsWeight;
    private Long requestedByAdminSeq;
    private Integer activeKey;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
