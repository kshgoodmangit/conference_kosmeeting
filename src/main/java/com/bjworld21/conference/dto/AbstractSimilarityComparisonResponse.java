package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityComparisonResponse {
    private AbstractSimilarityContentResponse source;
    private AbstractSimilarityContentResponse target;
    private AbstractSimilarityMatchResponse similarity;
    private LocalDateTime analyzedAt;
    private Boolean stale;
}
