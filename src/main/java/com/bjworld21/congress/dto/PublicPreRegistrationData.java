package com.bjworld21.congress.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PublicPreRegistrationData {
    private PublicPreRegistrationData() {
    }

    @Data
    public static class Request {
        private Long categorySeq;
        private List<PreRegistrationOptionData.Selection> options;
        private Boolean privacyAgreed;
        private Boolean termsAgreed;
    }

    public record Member(
            String memberType,
            String email,
            String firstName,
            String lastName,
            String institution,
            String country,
            String mobile
    ) {
    }

    public record Category(
            Long categorySeq,
            String categoryCode,
            String categoryName,
            String description,
            BigDecimal feeAmount
    ) {
    }

    public record Registration(
            Long seq,
            String registrationNumber,
            Long categorySeq,
            String categoryName,
            String periodType,
            String currency,
            BigDecimal feeAmount,
            BigDecimal optionAmount,
            BigDecimal totalAmount,
            List<PreRegistrationOptionData.Item> options,
            String applicationStatus,
            String paymentStatus
    ) {
    }

    public record Form(
            Member member,
            String periodType,
            String periodLabel,
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            String currency,
            List<Category> categories,
            List<PreRegistrationOptionData.CatalogItem> options,
            Registration registration,
            boolean registrationOpen,
            boolean paymentAvailable,
            String message
    ) {
    }
}
