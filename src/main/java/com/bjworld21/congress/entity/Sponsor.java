package com.bjworld21.congress.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sponsor {
    private Long seq;
    private Long conferenceSeq;
    private Long sponsorTypeCode;
    private String sponsorTypeName;
    private String sponsorName;
    private String linkUrl;
    private String logoOriFilename;
    private String logoSaveFilename;
    private LocalDate useStartDate;
    private LocalDate useEndDate;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
