package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerAssignmentCandidateResponse {
    private Long reviewerSeq;
    private String reviewerName;
    private String affiliation;
    private String department;
    private String positionTitle;
    private String contactEmail;
    private String expertiseNames;
    private Boolean expertiseMatched;
    private Integer activeAssignmentCount;
    private Long assignmentSeq;
    private String assignmentStatus;
    private LocalDateTime dueAt;
}
