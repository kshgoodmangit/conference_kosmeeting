package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractEvaluationItemRequest {
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
}
