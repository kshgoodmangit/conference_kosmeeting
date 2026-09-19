package com.bjworld21.conference.dto;

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
public class PreRegistrationResponse {
    private Long seq;
    private String registrationNumber;
    private Long memberSeq;
    private Long conferenceSeq;
    private String eventName;

    private String memberType;
    private String email;
    private String firstName;
    private String lastName;
    private String institution;
    private String department;
    private String positionTitle;
    private String country;
    private String mobile;

    private Long categorySeq;
    private String categoryCode;
    private String categoryName;
    private String periodType;
    private String currency;
    private BigDecimal feeAmount;
    private BigDecimal optionAmount;
    private BigDecimal totalAmount;
    private java.util.List<PreRegistrationOptionData.Item> options;

    private String applicationStatus;
    private String paymentStatus;
    private String paymentMethod;
    private String paymentTransactionId;
    private BigDecimal paidAmount;
    private LocalDateTime paidAt;

    private Boolean privacyAgreed;
    private LocalDateTime privacyAgreedAt;
    private Boolean termsAgreed;
    private LocalDateTime termsAgreedAt;
    private LocalDateTime cancelledAt;
    private String adminMemo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
