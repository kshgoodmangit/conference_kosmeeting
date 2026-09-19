package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractEvaluationItem {
    private Long seq;
    private Long conferenceSeq;
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
    private String isDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
