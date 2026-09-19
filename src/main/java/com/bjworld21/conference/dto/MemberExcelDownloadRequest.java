package com.bjworld21.conference.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Data
public class MemberExcelDownloadRequest implements ExcelDownloadRequest {
    private String reason;
    private String keyword;
    private String memberType;
    private Boolean hasPreRegistration;
    private Boolean hasAbstractSubmission;

    @Override
    public Map<String, Object> auditFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("keyword", keyword == null ? "" : keyword.trim());
        String normalizedMemberType = trimToNull(memberType);
        filters.put("memberType", normalizedMemberType == null ? null : normalizedMemberType.toLowerCase(Locale.ROOT));
        filters.put("hasPreRegistration", hasPreRegistration);
        filters.put("hasAbstractSubmission", hasAbstractSubmission);
        return filters;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
