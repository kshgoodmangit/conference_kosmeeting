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
public class MemberAbstractSubmissionResponse {
    private Long seq;
    private String submissionNo;
    private String presentationTypeName;
    private String categoryName;
    private String title;
    private String status;
    private Integer authorCount;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
}
