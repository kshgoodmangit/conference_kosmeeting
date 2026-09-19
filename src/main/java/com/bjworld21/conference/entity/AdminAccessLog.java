package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAccessLog {
    private Long seq;
    private Long adminSeq;
    private String adminEmail;
    private String adminName;
    private String adminRole;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime loginAt;
}
