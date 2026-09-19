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
public class RegistrationFeeCategoryResponse {
    private Long seq;
    private String categoryCode;
    private String categoryName;
    private String description;
    private Integer sortOrder;
    private String isUsed;
    private BigDecimal earlyBirdUsdFee;
    private BigDecimal earlyBirdKrwFee;
    private BigDecimal regularUsdFee;
    private BigDecimal regularKrwFee;
    private LocalDateTime updatedAt;
}
