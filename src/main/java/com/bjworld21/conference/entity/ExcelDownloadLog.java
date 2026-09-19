package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelDownloadLog {
    private Long seq;
    private String exportType;
    private String menuKey;
    private String menuName;
    private String reason;
    private String filterJson;
    private Long adminSeq;
    private String adminEmail;
    private String adminName;
    private String adminRole;
    private String ipAddress;
    private String userAgent;
    private String status;
    private Integer rowCount;
    private String fileName;
    private Long fileSize;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
}
