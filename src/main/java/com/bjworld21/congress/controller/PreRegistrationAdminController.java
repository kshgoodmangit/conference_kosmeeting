package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.PreRegistrationCategoryOptionResponse;
import com.bjworld21.congress.dto.PreRegistrationPageResponse;
import com.bjworld21.congress.dto.PreRegistrationResponse;
import com.bjworld21.congress.dto.PreRegistrationUpdateRequest;
import com.bjworld21.congress.dto.PreRegistrationCreateRequest;
import com.bjworld21.congress.dto.PreRegistrationOptionData;
import com.bjworld21.congress.dto.PreRegistrationExcelDownloadRequest;
import com.bjworld21.congress.service.AuditedExcelExportResult;
import com.bjworld21.congress.service.ExcelDownloadAuditService;
import com.bjworld21.congress.service.ExcelExportType;
import com.bjworld21.congress.service.PreRegistrationAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/pre-registrations")
public class PreRegistrationAdminController {
    private final PreRegistrationAdminService service;
    private final ExcelDownloadAuditService excelDownloadAuditService;

    public PreRegistrationAdminController(
            PreRegistrationAdminService service,
            ExcelDownloadAuditService excelDownloadAuditService
    ) {
        this.service = service;
        this.excelDownloadAuditService = excelDownloadAuditService;
    }

    @GetMapping
    public ResponseEntity<PreRegistrationPageResponse> findPage(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long categorySeq,
            @RequestParam(required = false) Long optionSeq,
            @RequestParam(defaultValue = "") String periodType,
            @RequestParam(defaultValue = "") String applicationStatus,
            @RequestParam(defaultValue = "") String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(service.findPage(
                conferenceSeq,
                page,
                size,
                keyword,
                categorySeq,
                optionSeq,
                periodType,
                applicationStatus,
                paymentStatus,
                dateFrom,
                dateTo
        ));
    }

    @GetMapping("/{seq}")
    public ResponseEntity<PreRegistrationResponse> findDetail(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        return ResponseEntity.ok(service.findDetail(conferenceSeq, seq));
    }

    @PostMapping
    public ResponseEntity<PreRegistrationResponse> create(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody PreRegistrationCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(conferenceSeq, request));
    }

    @GetMapping("/available-options")
    public ResponseEntity<List<PreRegistrationOptionData.CatalogItem>> findAvailableOptions(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(required = false) Long preRegistrationSeq,
            @RequestParam(defaultValue = "USD") String currency
    ) {
        return ResponseEntity.ok(service.findAvailableOptions(conferenceSeq, preRegistrationSeq, currency));
    }

    @PostMapping("/{seq}/cancel-payment")
    public ResponseEntity<PreRegistrationResponse> cancelPayment(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        return ResponseEntity.ok(service.cancelPayment(conferenceSeq, seq));
    }

    @PutMapping("/{seq}")
    public ResponseEntity<PreRegistrationResponse> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestBody PreRegistrationUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(conferenceSeq, seq, request));
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        service.delete(conferenceSeq, seq);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<PreRegistrationCategoryOptionResponse>> findCategoryOptions(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        return ResponseEntity.ok(service.findCategoryOptions(conferenceSeq));
    }

    @GetMapping("/filter-options")
    public ResponseEntity<List<PreRegistrationOptionData.FilterOption>> findFilterOptions(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        return ResponseEntity.ok(service.findFilterOptions(conferenceSeq));
    }

    @PostMapping("/excel")
    public ResponseEntity<?> downloadExcel(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody PreRegistrationExcelDownloadRequest request,
            HttpSession session,
            HttpServletRequest servletRequest
    ) {
        try {
            AuditedExcelExportResult audited = excelDownloadAuditService.execute(
                    ExcelExportType.PRE_REGISTRATIONS,
                    ((Number) session.getAttribute("adminSeq")).longValue(),
                    request.getReason(),
                    request.auditFilters(),
                    servletRequest,
                    () -> service.createExcel(
                            conferenceSeq,
                            request.getKeyword(), request.getCategorySeq(), request.getOptionSeq(), request.getPeriodType(),
                            request.getApplicationStatus(), request.getPaymentStatus(),
                            request.getDateFrom(), request.getDateTo()
                    )
            );
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(audited.exportResult().filename(), StandardCharsets.UTF_8)
                            .build().toString())
                    .header("X-Excel-Download-Log-Id", String.valueOf(audited.logSeq()))
                    .contentLength(audited.exportResult().content().length)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(audited.exportResult().content());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body("엑셀 파일을 생성하지 못했습니다.");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(PreRegistrationAdminService.PreRegistrationNotFoundException.class)
    public ResponseEntity<String> handleNotFound(PreRegistrationAdminService.PreRegistrationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }
}
