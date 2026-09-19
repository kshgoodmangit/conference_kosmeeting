package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberListResponse {
    private Long seq;
    private String memberType;
    private String email;
    private String firstName;
    private String lastName;
    private String institution;
    private String department;
    private String positionTitle;
    private String country;
    private String mobile;
    private Boolean newsletter;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
