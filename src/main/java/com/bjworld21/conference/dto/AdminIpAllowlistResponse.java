package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminIpAllowlistResponse {
    private Long seq;
    private String ruleName;
    private String ipCidr;
    private String description;
    private LocalDate useStartDate;
    private LocalDate useEndDate;
    private Boolean enabled;
    private boolean activeNow;
    private Long createdByAdminSeq;
    private Long updatedByAdminSeq;
    private String createdByAdminName;
    private String updatedByAdminName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
