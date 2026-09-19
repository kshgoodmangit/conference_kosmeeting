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
public class RegistrationCategory {
    private Long seq;
    private Long conferenceSeq;
    private String categoryCode;
    private String categoryName;
    private String description;
    private Integer sortOrder;
    private String isUsed;
    private String isDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
