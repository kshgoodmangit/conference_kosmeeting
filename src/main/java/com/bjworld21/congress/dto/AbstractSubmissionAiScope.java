package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionAiScope {
    private Long seq;
    private Long abstractSeq;
    private Long aiScopeCode;
    private String aiScopeName;
    private String isEtc;
    private String otherScopeText;
}
