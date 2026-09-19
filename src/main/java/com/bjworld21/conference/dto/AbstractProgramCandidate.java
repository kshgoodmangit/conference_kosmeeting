package com.bjworld21.conference.dto;

import lombok.Data;

@Data
public class AbstractProgramCandidate {
    private Long seq;
    private String submissionNo;
    private String title;
    private Long acceptedPresentationTypeCode;
    private String acceptedPresentationTypeName;
    private Long categoryCode;
    private String categoryName;
    private String presenterName;
    private String affiliation;
    private Integer presenterCount;
    private Integer assignmentCount;
}
