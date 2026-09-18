package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardRecentAbstractResponse {
    private Long seq;
    private String submissionNo;
    private String title;
    private String memberFullName;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
}
