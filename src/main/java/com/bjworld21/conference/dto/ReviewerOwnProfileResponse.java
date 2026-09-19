package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerOwnProfileResponse {
    private Long seq;
    private String email;
    private String adminName;
    private String role;
    private String status;
    private ReviewerProfileResponse reviewerProfile;
}
