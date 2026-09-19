package com.bjworld21.conference.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RegistrationOptionData {
    private RegistrationOptionData() {}

    @Data
    public static class Option {
        private Long seq;
        private Long conferenceSeq;
        private String optionName;
        private String description;
        private BigDecimal krwPrice;
        private BigDecimal usdPrice;
        private LocalDateTime saleStartsAt;
        private LocalDateTime saleEndsAt;
        private LocalDateTime changeEndsAt;
        private Integer capacity;
        private Integer maxPerPerson;
        private Boolean enabled;
        private Integer sortOrder;
        private Integer versionNo;
    }

    @Data
    public static class Summary {
        private long totalCount;
        private long enabledCount;
        private long disabledCount;
    }

    public record Page(List<Option> items, int page, int totalPages, Summary summary) {}
}
