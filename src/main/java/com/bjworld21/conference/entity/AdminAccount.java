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
public class AdminAccount {
    private Long seq;
    private String email;
    private String password;
    private String adminName;
    private String affiliation;
    private String department;
    private String positionTitle;
    private String phoneNumber;
    private String contactEmail;
    private String role; // admin, reviewer, maintenance
    private String status; // active, inactive
    private LocalDateTime lastLoginAt;
    private int loginFailureCount;
    private LocalDateTime loginLockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

