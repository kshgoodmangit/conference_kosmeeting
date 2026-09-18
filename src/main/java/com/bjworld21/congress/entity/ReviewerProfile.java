package com.bjworld21.congress.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerProfile {
    private Long seq;
    private Long conferenceSeq;
    private Long adminSeq;
    private String affiliation;
    private String department;
    private String positionTitle;
    private String phoneNumber;
    private String contactEmail;
    private String isUsed;
    private String isDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
