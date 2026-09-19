package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreRegistrationSummary {
    private long totalCount;
    private long submittedCount;
    private long cancelledCount;
    private long paidCount;
    private long unpaidCount;
    private BigDecimal totalPaidKrwAmount;
    private BigDecimal totalPaidUsdAmount;
}
