package com.bjworld21.congress.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminAccessRequest {
    private Long seq;
    private String siteUrl;
    private String requestIp;
    private String affiliation;
    private String requesterName;
    private String contact;
    private String purpose;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Long processedByAdminSeq;
    private String processedByName;
    private LocalDateTime processedAt;
    private Long allowlistSeq;
    private String mailStatus;
}
