package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerOwnProfileUpdateRequest {
    private String adminName;
    private String affiliation;
    private String department;
    private String positionTitle;
    private String phoneNumber;
    private String contactEmail;
    private List<Long> expertiseCodes;
}
