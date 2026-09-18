package com.bjworld21.congress.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AbstractExcelDownloadRequest implements ExcelDownloadRequest {
    private String reason;
    private String keyword;
    private Long presentationTypeCode;
    private Long acceptedPresentationTypeCode;
    private Long categoryCode;
    private String status;

    @Override
    public Map<String, Object> auditFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("keyword", keyword == null ? "" : keyword.trim());
        filters.put("presentationTypeCode", presentationTypeCode);
        filters.put("acceptedPresentationTypeCode", acceptedPresentationTypeCode);
        filters.put("categoryCode", categoryCode);
        filters.put("status", status == null ? "" : status.trim());
        return filters;
    }
}
