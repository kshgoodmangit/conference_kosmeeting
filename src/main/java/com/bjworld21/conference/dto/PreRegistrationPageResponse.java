package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreRegistrationPageResponse {
    private List<PreRegistrationResponse> items;
    private int page;
    private int size;
    private int totalPages;
    private long totalCount;
    private long submittedCount;
    private long cancelledCount;
    private long paidCount;
    private long unpaidCount;
    private BigDecimal totalPaidKrwAmount;
    private BigDecimal totalPaidUsdAmount;
}
