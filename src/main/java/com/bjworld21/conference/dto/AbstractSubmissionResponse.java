package com.bjworld21.conference.dto;

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
public class AbstractSubmissionResponse {
    private Long seq;
    private Long memberSeq;
    private String submissionSource;
    private Long createdByAdminSeq;
    private String createdByAdminName;
    private String memberEmail;
    private String memberFullName;
    private String submissionNo;
    private Long presentationTypeCode;
    private String presentationTypeName;
    private Long acceptedPresentationTypeCode;
    private String acceptedPresentationTypeName;
    private Long categoryCode;
    private String categoryName;
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
    private String decisionByAdminName;
    private LocalDateTime decisionAt;
    private String decisionReason;
    private Boolean forcedDecision;
    private Integer authorCount;
    private Integer institutionCount;
    private Integer reviewerAssignmentCount;
    private Integer completedReviewCount;
    private Double averageReviewScore;
    private Double titleSimilarityMaxScore;
    private Integer titleSimilarityMatchCount;
    private String titleSimilarityAlgorithmVersion;
    private LocalDateTime titleSimilarityCheckedAt;
    private String mainAuthorName;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AbstractSubmissionInstitution> institutions;
    private List<AbstractSubmissionAuthor> authors;
    private List<AbstractSubmissionAiTool> aiTools;
    private List<AbstractSubmissionAiScope> aiScopes;
    private List<AbstractSubmissionAttachmentResponse> attachments;
}
