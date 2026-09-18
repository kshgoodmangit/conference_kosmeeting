package com.bjworld21.congress.dto;

import java.util.Map;

public interface ExcelDownloadRequest {
    String getReason();

    Map<String, Object> auditFilters();
}
