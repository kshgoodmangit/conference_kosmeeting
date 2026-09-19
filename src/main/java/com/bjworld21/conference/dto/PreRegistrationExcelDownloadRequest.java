package com.bjworld21.conference.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Data
public class PreRegistrationExcelDownloadRequest implements ExcelDownloadRequest {
    private String reason;
    private String keyword;
    private Long categorySeq;
    private Long optionSeq;
    private String periodType;
    private String applicationStatus;
    private String paymentStatus;
    private LocalDate dateFrom;
    private LocalDate dateTo;

    @Override
    public Map<String, Object> auditFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("keyword", trimToNull(keyword));
        filters.put("categorySeq", categorySeq != null && categorySeq > 0 ? categorySeq : null);
        filters.put("optionSeq", optionSeq != null && optionSeq > 0 ? optionSeq : null);
        filters.put("periodType", upperToNull(periodType));
        filters.put("applicationStatus", upperToNull(applicationStatus));
        filters.put("paymentStatus", upperToNull(paymentStatus));
        filters.put("dateFrom", dateFrom);
        filters.put("dateTo", dateTo);
        return filters;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upperToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
