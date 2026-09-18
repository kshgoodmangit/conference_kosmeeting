package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.AbstractSubmissionMetaResponse;
import com.bjworld21.congress.dto.AbstractSubmissionPageResponse;
import com.bjworld21.congress.dto.AbstractSubmissionRequest;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.dto.AbstractSimilarityWeights;
import com.bjworld21.congress.dto.AbstractReviewAssignmentRequest;
import com.bjworld21.congress.dto.AbstractReviewAssignmentResponse;
import com.bjworld21.congress.dto.AbstractExcelDownloadRequest;
import com.bjworld21.congress.dto.AbstractDecisionRequest;
import com.bjworld21.congress.config.AdminRolePolicy;
import com.bjworld21.congress.dto.AbstractSubmissionAttachmentResponse;
import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.service.AbstractDecisionService;
import com.bjworld21.congress.service.AbstractSimilarityService;
import com.bjworld21.congress.service.AbstractSimilarityJobService;
import com.bjworld21.congress.service.AbstractPresentationAttachmentService;
import com.bjworld21.congress.service.AbstractReviewAssignmentBulkImportService;
import com.bjworld21.congress.service.AbstractReviewAssignmentService;
import com.bjworld21.congress.service.AbstractSubmissionService;
import com.bjworld21.congress.service.AbstractTitleSimilarityService;
import com.bjworld21.congress.service.AdminAbstractReviewResultService;
import com.bjworld21.congress.service.AuditedExcelExportResult;
import com.bjworld21.congress.service.ExcelDownloadAuditService;
import com.bjworld21.congress.service.ExcelExportType;
import com.bjworld21.congress.entity.AbstractSubmissionAttachment;
import com.bjworld21.congress.entity.AbstractSimilarityJob;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/abstracts")
@CrossOrigin(origins = "*")
public class AbstractController {
    private final AbstractSubmissionService abstractSubmissionService;
    private final AbstractReviewAssignmentService abstractReviewAssignmentService;
    private final AbstractReviewAssignmentBulkImportService abstractReviewAssignmentBulkImportService;
    private final AdminAbstractReviewResultService adminAbstractReviewResultService;
    private final ExcelDownloadAuditService excelDownloadAuditService;
    private final AbstractDecisionService abstractDecisionService;
    private final AbstractPresentationAttachmentService abstractPresentationAttachmentService;
    private final AbstractSimilarityService abstractSimilarityService;
    private final AbstractSimilarityJobService abstractSimilarityJobService;
    private final LicenseProperties licenseProperties;
    private final AbstractTitleSimilarityService abstractTitleSimilarityService;

    public AbstractController(
            AbstractSubmissionService abstractSubmissionService,
            AbstractReviewAssignmentService abstractReviewAssignmentService,
            AbstractReviewAssignmentBulkImportService abstractReviewAssignmentBulkImportService,
            AdminAbstractReviewResultService adminAbstractReviewResultService,
            ExcelDownloadAuditService excelDownloadAuditService,
            AbstractDecisionService abstractDecisionService,
            AbstractPresentationAttachmentService abstractPresentationAttachmentService,
            AbstractSimilarityService abstractSimilarityService,
            AbstractSimilarityJobService abstractSimilarityJobService,
            LicenseProperties licenseProperties,
            AbstractTitleSimilarityService abstractTitleSimilarityService
    ) {
        this.abstractSubmissionService = abstractSubmissionService;
        this.abstractReviewAssignmentService = abstractReviewAssignmentService;
        this.abstractReviewAssignmentBulkImportService = abstractReviewAssignmentBulkImportService;
        this.adminAbstractReviewResultService = adminAbstractReviewResultService;
        this.excelDownloadAuditService = excelDownloadAuditService;
        this.abstractDecisionService = abstractDecisionService;
        this.abstractPresentationAttachmentService = abstractPresentationAttachmentService;
        this.abstractSimilarityService = abstractSimilarityService;
        this.abstractSimilarityJobService = abstractSimilarityJobService;
        this.licenseProperties = licenseProperties;
        this.abstractTitleSimilarityService = abstractTitleSimilarityService;
    }

    @GetMapping("/meta")
    public ResponseEntity<AbstractSubmissionMetaResponse> getMeta() {
        return ResponseEntity.ok(abstractSubmissionService.getMeta());
    }

    @GetMapping
    public ResponseEntity<AbstractSubmissionPageResponse> list(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long presentationTypeCode,
            @RequestParam(required = false) Long acceptedPresentationTypeCode,
            @RequestParam(required = false) Long categoryCode,
            @RequestParam(defaultValue = "") String status
    ) {
        return ResponseEntity.ok(abstractSubmissionService.findPage(
                conferenceSeq, page, size, keyword, presentationTypeCode, acceptedPresentationTypeCode, categoryCode, status
        ));
    }

    @PostMapping(value = "/review-assignments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importReviewAssignments(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueAt,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            Long adminSeq = ((Number) session.getAttribute("adminSeq")).longValue();
            return ResponseEntity.ok(abstractReviewAssignmentBulkImportService.importAssignments(
                    conferenceSeq, file, dueAt, adminSeq
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("심사자 일괄배정에 실패했습니다.");
        }
    }

    @GetMapping("/{seq}/review-results")
    public ResponseEntity<?> getReviewResults(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        try {
            return ResponseEntity.ok(adminAbstractReviewResultService.getReviewResults(conferenceSeq, seq));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("심사 결과를 불러오지 못했습니다.");
        }
    }

    @PostMapping("/export")
    public ResponseEntity<?> export(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody AbstractExcelDownloadRequest request,
            HttpSession session,
            HttpServletRequest servletRequest
    ) {
        try {
            AuditedExcelExportResult audited = excelDownloadAuditService.execute(
                    ExcelExportType.ABSTRACTS,
                    ((Number) session.getAttribute("adminSeq")).longValue(),
                    request.getReason(),
                    request.auditFilters(),
                    servletRequest,
                    () -> abstractSubmissionService.exportXlsx(
                            conferenceSeq,
                            request.getKeyword(), request.getPresentationTypeCode(),
                            request.getAcceptedPresentationTypeCode(), request.getCategoryCode(), request.getStatus()
                    )
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(audited.exportResult().filename(), StandardCharsets.UTF_8).build());
            headers.setContentLength(audited.exportResult().content().length);
            headers.set("X-Excel-Download-Log-Id", String.valueOf(audited.logSeq()));

            return new ResponseEntity<>(audited.exportResult().content(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("엑셀 파일을 생성하지 못했습니다.");
        }
    }

    @GetMapping("/{seq}/review-assignments")
    public ResponseEntity<?> getReviewAssignments(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            return ResponseEntity.ok(abstractReviewAssignmentService.getAssignments(conferenceSeq, seq));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("심사자 할당 정보를 불러오지 못했습니다.");
        }
    }

    @GetMapping("/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<?> downloadAttachment(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @PathVariable Long attachmentSeq
    ) {
        try {
            AbstractSubmissionAttachment attachment = abstractSubmissionService.requireAttachment(conferenceSeq, seq, attachmentSeq);
            Resource resource = abstractSubmissionService.getAttachmentResource(attachment);
            boolean isPdf = "pdf".equalsIgnoreCase(attachment.getFileExtension())
                    || MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(attachment.getContentType());
            ContentDisposition disposition = (isPdf ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8)
                    .build();
            MediaType contentType;
            try {
                contentType = MediaType.parseMediaType(attachment.getContentType());
            } catch (Exception ignored) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(contentType)
                    .contentLength(attachment.getFileSize())
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("첨부파일을 다운로드하지 못했습니다.");
        }
    }

    @PostMapping(value = "/{seq}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPresentationAttachment(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam("file") MultipartFile file,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            AbstractSubmissionAttachmentResponse response = abstractPresentationAttachmentService.add(conferenceSeq, seq, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("발표 자료를 업로드하지 못했습니다.");
        }
    }

    @DeleteMapping("/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<?> deletePresentationAttachment(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @PathVariable Long attachmentSeq,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            abstractPresentationAttachmentService.delete(conferenceSeq, seq, attachmentSeq);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("발표 자료를 삭제하지 못했습니다.");
        }
    }

    @PutMapping("/{seq}/review-assignments")
    public ResponseEntity<?> saveReviewAssignments(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestBody AbstractReviewAssignmentRequest request,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            Long adminSeq = ((Number) session.getAttribute("adminSeq")).longValue();
            AbstractReviewAssignmentResponse response = abstractReviewAssignmentService.saveAssignments(
                    conferenceSeq, seq, request, adminSeq
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("심사자 할당 저장에 실패했습니다.");
        }
    }

    @GetMapping("/{seq}")
    public ResponseEntity<?> getBySeq(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        try {
            AbstractSubmissionResponse response = abstractSubmissionService.getBySeq(conferenceSeq, seq);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to load abstract details.");
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody AbstractSubmissionRequest request,
            HttpSession session
    ) {
        try {
            Long adminSeq = ((Number) session.getAttribute("adminSeq")).longValue();
            AbstractSubmissionResponse response = abstractSubmissionService.createByAdmin(conferenceSeq, request, adminSeq);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create abstract.");
        }
    }

    @PutMapping("/{seq}")
    public ResponseEntity<?> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestBody AbstractSubmissionRequest request
    ) {
        try {
            AbstractSubmissionResponse response = abstractSubmissionService.update(conferenceSeq, seq, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update abstract.");
        }
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        try {
            abstractSubmissionService.delete(conferenceSeq, seq);
            return ResponseEntity.ok("Abstract deleted successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete abstract.");
        }
    }

    @GetMapping("/{seq}/similarities")
    public ResponseEntity<?> getSimilarities(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
        try {
            if (abstractSimilarityJobService.getActive(conferenceSeq) != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("전체 유사도 분석이 진행 중입니다. 완료된 후 결과를 확인해 주세요.");
            }
            return ResponseEntity.ok(abstractSimilarityService.getStoredAnalysis(conferenceSeq, seq));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("유사도 분석 결과를 불러오지 못했습니다.");
        }
    }

    @GetMapping("/{seq}/similarities/{targetSeq}")
    public ResponseEntity<?> getSimilarityComparison(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @PathVariable Long targetSeq,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
        try {
            if (abstractSimilarityJobService.getActive(conferenceSeq) != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("전체 유사도 분석이 진행 중입니다. 완료된 후 결과를 확인해 주세요.");
            }
            return ResponseEntity.ok(abstractSimilarityService.getStoredComparison(conferenceSeq, seq, targetSeq));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("유사 초록 상세 비교 정보를 불러오지 못했습니다.");
        }
    }

    @PostMapping("/similarity-jobs")
    public ResponseEntity<?> startSimilarityJob(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody(required = false) AbstractSimilarityWeights weights,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
        try {
            Long adminSeq = ((Number) session.getAttribute("adminSeq")).longValue();
            AbstractSimilarityWeights resolvedWeights = weights == null
                    ? abstractSimilarityJobService.getDefaultWeights()
                    : weights;
            return ResponseEntity.accepted().body(
                    abstractSimilarityJobService.start(conferenceSeq, adminSeq, resolvedWeights)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("AI 유사도 분석 실행에 실패했습니다.");
        }
    }

    @GetMapping("/{seq}/title-similarities")
    public ResponseEntity<?> getTitleSimilarities(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            AbstractSubmissionResponse submission = abstractSubmissionService.getBySeq(conferenceSeq, seq);
            return ResponseEntity.ok(abstractTitleSimilarityService.getStoredResult(
                    conferenceSeq,
                    seq,
                    submission.getTitleSimilarityMaxScore(),
                    submission.getTitleSimilarityMatchCount(),
                    submission.getTitleSimilarityAlgorithmVersion(),
                    submission.getTitleSimilarityCheckedAt()
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("제목 유사도 검사 결과를 불러오지 못했습니다.");
        }
    }

    @GetMapping("/similarity-jobs/default-weights")
    public ResponseEntity<?> getDefaultSimilarityWeights(HttpSession session) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
        try {
            return ResponseEntity.ok(abstractSimilarityJobService.getDefaultWeights());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/similarity-jobs/active")
    public ResponseEntity<?> getActiveSimilarityJob(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
        AbstractSimilarityJob job = abstractSimilarityJobService.getActive(conferenceSeq);
        return job == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(job);
    }

    @GetMapping("/similarity-jobs/{jobSeq}")
    public ResponseEntity<?> getSimilarityJob(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long jobSeq,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
        try {
            return ResponseEntity.ok(abstractSimilarityJobService.get(conferenceSeq, jobSeq));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping(value = "/similarity-jobs/{jobSeq}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> streamSimilarityJob(
            @RequestHeader(value = "X-Conference-Seq", required = false) Long conferenceHeaderSeq,
            @RequestParam(value = "conferenceSeq", required = false) Long conferenceParameterSeq,
            @PathVariable Long jobSeq,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
        Long conferenceSeq = conferenceHeaderSeq != null ? conferenceHeaderSeq : conferenceParameterSeq;
        if (conferenceSeq == null) {
            return ResponseEntity.badRequest().body("학회 정보가 필요합니다.");
        }
        try {
            SseEmitter emitter = abstractSimilarityJobService.subscribe(conferenceSeq, jobSeq);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                    .header("X-Accel-Buffering", "no")
                    .body(emitter);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/{seq}/decision")
    public ResponseEntity<?> decide(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestBody AbstractDecisionRequest request,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireAdminAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            Long adminSeq = ((Number) session.getAttribute("adminSeq")).longValue();
            return ResponseEntity.ok(abstractDecisionService.decide(conferenceSeq, seq, adminSeq, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("최종 결정을 저장하지 못했습니다.");
        }
    }

    private ResponseEntity<String> requireAdminAccess(HttpSession session) {
        Object adminSeq = session.getAttribute("adminSeq");
        if (!(adminSeq instanceof Number)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("관리자 로그인이 필요합니다.");
        }
        if (!AdminRolePolicy.isFullAdministrator(session.getAttribute("adminRole"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자만 사용할 수 있는 기능입니다.");
        }
        return null;
    }
}
