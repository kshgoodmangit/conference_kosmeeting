package com.bjworld21.congress.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Links an exported copy to the server audit log; this is not tamper-proof evidence. */
final class ExcelDownloadTrace {
    static final String SHEET_NAME = "_download_info";
    static final String LOG_ID_PROPERTY = "ICMS.DownloadLogId";
    static final String TYPE_PROPERTY = "ICMS.ExportType";
    static final String VERSION_PROPERTY = "ICMS.TraceVersion";

    private ExcelDownloadTrace() {
    }

    static ExcelExportResult attach(ExcelExportResult source, long logSeq, ExcelExportType exportType) {
        if (logSeq <= 0) {
            throw new IllegalArgumentException("다운로드 이력 번호가 올바르지 않습니다.");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source.content()));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            // Never overwrite an existing data sheet or stale trace silently.
            if (workbook.getSheet(SHEET_NAME) != null) {
                throw new IllegalStateException("엑셀 추적정보 시트가 이미 존재합니다.");
            }
            String logId = Long.toString(logSeq);
            var properties = workbook.getProperties().getCustomProperties();
            properties.addProperty(VERSION_PROPERTY, "1");
            properties.addProperty(LOG_ID_PROPERTY, logId);
            properties.addProperty(TYPE_PROPERTY, exportType.name());

            XSSFSheet trace = workbook.createSheet(SHEET_NAME);
            addRow(trace, 0, "traceVersion", "1");
            // IDs are text so Excel does not round BIGINT values after 15 digits.
            addRow(trace, 1, "downloadLogId", logId);
            addRow(trace, 2, "exportType", exportType.name());
            workbook.setSheetVisibility(workbook.getSheetIndex(trace), SheetVisibility.VERY_HIDDEN);
            workbook.write(output);
            return new ExcelExportResult(output.toByteArray(), source.rowCount(), source.filename());
        } catch (IOException exception) {
            throw new IllegalStateException("엑셀 다운로드 추적정보를 저장하지 못했습니다.", exception);
        }
    }

    private static void addRow(XSSFSheet sheet, int index, String key, String value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
    }
}
