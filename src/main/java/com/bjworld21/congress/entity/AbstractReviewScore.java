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
public class AbstractReviewScore {
    private Long seq;
    private Long reviewSeq;
    private Long evaluationItemSeq;
    private Integer score;
    private String itemComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
