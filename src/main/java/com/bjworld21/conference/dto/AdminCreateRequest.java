package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCreateRequest {
    private String email;
    private String password;
    private String adminName;
    private String affiliation;
    private String department;
    private String positionTitle;
    private String phoneNumber;
    private String contactEmail;
    private String role; // admin, reviewer, maintenance
    private ReviewerProfileRequest reviewerProfile;
}

