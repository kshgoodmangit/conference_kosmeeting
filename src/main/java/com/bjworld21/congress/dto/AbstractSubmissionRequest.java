package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionRequest {
    private Long seq;
    private Long conferenceSeq;
    private Long memberSeq;
    private String memberId;
    private String submissionSource;
    private Long createdByAdminSeq;
    private String submissionNo;
    private Long presentationTypeCode;
    private Long acceptedPresentationTypeCode;
    private Long categoryCode;
    private String title;
    private String objectiveText;
    private String methodsText;
    private String resultsText;
    private String conclusionsText;
    private Boolean aiUsage;
    private String aiVersionInfo;
    private Boolean aiDataAnalysisUsed;
    private Boolean plagiarismPolicyConfirmed;
    private Integer wordCount;
    private String status;
    private Long decisionByAdminSeq;
    private LocalDateTime decisionAt;
    private String decisionReason;
    private Boolean forcedDecision;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private List<AbstractSubmissionInstitution> institutions;
    private List<AbstractSubmissionAuthor> authors;
    private List<AbstractSubmissionAiTool> aiTools;
    private List<AbstractSubmissionAiScope> aiScopes;
}
