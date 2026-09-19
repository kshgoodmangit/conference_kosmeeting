package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public final class PreRegistrationOptionData {
    private PreRegistrationOptionData() {}

    public record Selection(Long optionSeq, Integer quantity) {}

    public record FilterOption(Long optionSeq, String optionName, String eventName) {}

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CatalogItem {
        private Long optionSeq;
        private String optionName;
        private String description;
        private String currency;
        private BigDecimal unitPrice;
        private Integer maxPerPerson;
        private Integer remainingCapacity;
        private Boolean enabled;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Item {
        private Long seq;
        private Long optionSeq;
        private String optionName;
        private String optionDescription;
        private String currency;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal amount;
    }
}
