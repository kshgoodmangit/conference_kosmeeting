package com.bjworld21.congress.service;

public record ExcelExportResult(byte[] content, int rowCount, String filename) {
    public ExcelExportResult {
        if (content == null) {
            throw new IllegalArgumentException("엑셀 파일 내용이 없습니다.");
        }
        if (rowCount < 0) {
            throw new IllegalArgumentException("엑셀 결과 행 수가 올바르지 않습니다.");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("엑셀 파일명이 없습니다.");
        }
    }
}
