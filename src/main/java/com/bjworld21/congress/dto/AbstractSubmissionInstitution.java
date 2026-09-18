package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionInstitution {
    private Long seq;
    private Long abstractSeq;
    private Integer institutionNo;
    private String country;
    private String institutionName;
    private String department;
}
