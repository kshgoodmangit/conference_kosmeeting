package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramDayRequest {
    private LocalDate eventDate;
    private Integer dayNumber;
    private String dayTitle;
    private String theme;
    private Integer sortOrder;
    private Boolean enabled;
}
