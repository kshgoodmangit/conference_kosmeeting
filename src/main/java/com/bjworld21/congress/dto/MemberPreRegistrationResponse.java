package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberPreRegistrationResponse {
    private Long seq;
    private String registrationNumber;
    private String categoryName;
    private String periodType;
    private String currency;
    private BigDecimal feeAmount;
    private String applicationStatus;
    private String paymentStatus;
    private BigDecimal paidAmount;
    private LocalDateTime createdAt;
}
