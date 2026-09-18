package com.bjworld21.congress.service;

public record AuditedExcelExportResult(long logSeq, ExcelExportResult exportResult) {
}
