package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminResponse {
    private Long seq;
    private String email;
    private String adminName;
    private String affiliation;
    private String department;
    private String positionTitle;
    private String phoneNumber;
    private String contactEmail;
    private String role;
    private String status;
    private ReviewerProfileResponse reviewerProfile;
    private java.time.LocalDateTime lastLoginAt;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private String message;
}

