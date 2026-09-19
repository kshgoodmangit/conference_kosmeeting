package com.bjworld21.conference.service;

public record AuditedExcelExportResult(long logSeq, ExcelExportResult exportResult) {
}
