package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractSimilarityResponse {
    private Long abstractSeq;
    private String model;
    private Integer dimension;
    private Integer comparedCount;
    private LocalDateTime analyzedAt;
    private Boolean stale;
    private List<AbstractSimilarityMatchResponse> matches;
}
