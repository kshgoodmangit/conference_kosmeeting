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
public class AdminIpAllowlistRequest {
    private String ruleName;
    private String ipCidr;
    private String description;
    private LocalDate useStartDate;
    private LocalDate useEndDate;
    private Boolean enabled;
}
