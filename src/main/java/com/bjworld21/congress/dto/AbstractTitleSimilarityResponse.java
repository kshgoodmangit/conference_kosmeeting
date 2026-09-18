package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractTitleSimilarityResponse {
    private Long abstractSeq;
    private Double warningThreshold;
    private Double maxSimilarity;
    private Integer matchCount;
    private String algorithmVersion;
    private LocalDateTime checkedAt;
    private List<AbstractTitleSimilarityMatchResponse> matches;
}
