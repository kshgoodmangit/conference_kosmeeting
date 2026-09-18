package com.bjworld21.congress.dto;

import lombok.Data;

@Data
public class AbstractDecisionRequest {
    private String decision;
    private Long acceptedPresentationTypeCode;
    private String reason;
}
