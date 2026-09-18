package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionAiTool {
    private Long seq;
    private Long abstractSeq;
    private Long aiToolCode;
    private String aiToolName;
    private String isEtc;
    private String otherToolName;
    private String otherProviderName;
}
