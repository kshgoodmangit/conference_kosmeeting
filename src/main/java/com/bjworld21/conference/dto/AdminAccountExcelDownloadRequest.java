package com.bjworld21.conference.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AdminAccountExcelDownloadRequest implements ExcelDownloadRequest {
    private String reason;
    private String keyword;

    @Override
    public Map<String, Object> auditFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("keyword", keyword == null ? "" : keyword.trim());
        return filters;
    }
}
