package com.bjworld21.conference.dto;

import lombok.Data;

@Data
public class AbstractDecisionRequest {
    private String decision;
    private Long acceptedPresentationTypeCode;
    private String reason;
}
