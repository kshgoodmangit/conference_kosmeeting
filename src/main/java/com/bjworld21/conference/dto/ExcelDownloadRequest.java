package com.bjworld21.conference.dto;

import java.util.Map;

public interface ExcelDownloadRequest {
    String getReason();

    Map<String, Object> auditFilters();
}
