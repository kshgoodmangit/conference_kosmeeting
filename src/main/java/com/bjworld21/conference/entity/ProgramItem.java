package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramItem {
    private Long seq;
    private Long programDaySeq;
    private Long roomSeq;
    private Long parentSeq;
    private Long abstractSubmissionSeq;
    private String scopeType;
    private String itemType;
    private LocalTime startTime;
    private LocalTime endTime;
    private String title;
    private String subtitle;
    private String organizerText;
    private String speakerText;
    private String chairText;
    private String notes;
    private String rowStyle;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
