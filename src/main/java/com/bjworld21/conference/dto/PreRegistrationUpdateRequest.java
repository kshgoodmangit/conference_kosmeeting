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
public class PreRegistrationUpdateRequest {
    private Long categorySeq;
    private String periodType;
    private String currency;
    private BigDecimal feeAmount;
    private List<PreRegistrationOptionData.Selection> options;
    private String applicationStatus;
    private String paymentStatus;
    private String adminMemo;
}
