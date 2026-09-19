package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractEvaluationItemResponse {
    private Long seq;
    private String itemName;
    private String description;
    private Integer sortOrder;
    private String isUsed;
    private String score1Guide;
    private String score2Guide;
    private String score3Guide;
    private String score4Guide;
    private String score5Guide;
    private String score6Guide;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
