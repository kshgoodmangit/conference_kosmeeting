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
public class AdminIpAccessStatusResponse {
    private boolean enabled;
    private int cachedRuleCount;
    private String currentIp;
    private LocalDateTime reloadedAt;
}
