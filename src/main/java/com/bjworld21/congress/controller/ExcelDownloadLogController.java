package com.bjworld21.congress.controller;

import com.bjworld21.congress.service.ExcelDownloadLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/excel-download-logs")
public class ExcelDownloadLogController {
    private final ExcelDownloadLogService service;

    public ExcelDownloadLogController(ExcelDownloadLogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> findPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String exportType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String adminKeyword,
            @RequestParam(required = false) String reasonKeyword
    ) {
        try {
            return ResponseEntity.ok(service.findPage(
                    page, size, dateFrom, dateTo, exportType, status, adminKeyword, reasonKeyword
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @GetMapping("/{seq}")
    public ResponseEntity<?> findDetail(@PathVariable Long seq) {
        try {
            return ResponseEntity.ok(service.findDetail(seq));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
