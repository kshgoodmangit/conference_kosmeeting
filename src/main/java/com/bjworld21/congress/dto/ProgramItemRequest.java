package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramItemRequest {
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
    private List<ProgramItemPersonRequest> people;
}
