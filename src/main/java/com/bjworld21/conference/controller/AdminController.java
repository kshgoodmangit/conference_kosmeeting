package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AdminCreateRequest;
import com.bjworld21.conference.dto.AdminBulkImportResponse;
import com.bjworld21.conference.dto.AdminLoginRequest;
import com.bjworld21.conference.dto.AdminLoginResponse;
import com.bjworld21.conference.dto.AdminPageResponse;
import com.bjworld21.conference.dto.AdminResponse;
import com.bjworld21.conference.dto.AdminAccountExcelDownloadRequest;
import com.bjworld21.conference.dto.AbstractCategoryResponse;
import com.bjworld21.conference.dto.ReviewerProfileRequest;
import com.bjworld21.conference.dto.TestReviewerCreateResponse;
import com.bjworld21.conference.service.AdminAccessLogService;
import com.bjworld21.conference.service.AdminService;
import com.bjworld21.conference.service.AdminBulkImportService;
import com.bjworld21.conference.service.AuditedExcelExportResult;
import com.bjworld21.conference.service.ExcelDownloadAuditService;
import com.bjworld21.conference.service.ExcelExportType;
import com.bjworld21.conference.service.ConferenceSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AdminService adminService;

    @Autowired
    private AdminBulkImportService adminBulkImportService;

    @Autowired
    private AdminAccessLogService adminAccessLogService;

    @Autowired
    private ExcelDownloadAuditService excelDownloadAuditService;

    @Autowired
    private ConferenceSettingsService conferenceSettingsService;

    /**
     * 관리자 로그인
     * POST /api/admin/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestParam String email,
            @RequestParam String password,
            HttpServletRequest servletRequest,
            HttpSession session
    ) {
        try {
            AdminLoginRequest request = AdminLoginRequest.builder()
                    .email(email)
                    .password(password)
                    .build();

            AdminLoginResponse response = adminService.login(request);
            if (servletRequest.isRequestedSessionIdValid()) {
                servletRequest.changeSessionId();
            }
            session.setAttribute("adminSeq", response.getSeq());
            session.setAttribute("adminRole", response.getRole());
            if ("reviewer".equals(response.getRole())) {
                session.setAttribute("reviewerConferenceSeq", response.getConferenceSeq());
            } else {
                session.removeAttribute("reviewerConferenceSeq");
            }
            try {
                adminAccessLogService.recordSuccessfulLogin(response, servletRequest);
            } catch (Exception exception) {
                log.error("Failed to save admin login access log: adminSeq={}", response.getSeq(), exception);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("로그인 중 오류가 발생했습니다.");
        }
    }

    /**
     * 관리자 계정 생성 (최고관리자용)
     * POST /api/admin/accounts
     */
    @PostMapping("/accounts")
    public ResponseEntity<?> createAdmin(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String adminName,
            @RequestParam(defaultValue = "admin") String role,
            @RequestParam(required = false) String affiliation,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String positionTitle,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String contactEmail,
            @RequestParam(defaultValue = "Y") String isUsed,
            @RequestParam(required = false) List<String> expertiseCodes) {
        try {
            AdminCreateRequest request = AdminCreateRequest.builder()
                    .email(email)
                    .password(password)
                    .adminName(adminName)
                    .affiliation(affiliation)
                    .department(department)
                    .positionTitle(positionTitle)
                    .phoneNumber(phoneNumber)
                    .contactEmail(contactEmail)
                    .role(role)
                    .reviewerProfile(reviewerProfileRequest(
                            affiliation, department, positionTitle, phoneNumber,
                            contactEmail, isUsed, expertiseCodes))
                    .build();

            AdminResponse response = adminService.create(conferenceSeq, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("관리자 계정 생성 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public ResponseEntity<?> getSession(
            @RequestHeader(value = "X-Conference-Seq", required = false) Long conferenceSeq,
            HttpServletRequest servletRequest
    ) {
        HttpSession session = servletRequest.getSession(false);
        if (session == null || !(session.getAttribute("adminSeq") instanceof Number adminSeq)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Object sessionRole = session.getAttribute("adminRole");
            Long resolvedConferenceSeq;
            if ("reviewer".equals(sessionRole)) {
                resolvedConferenceSeq = adminService.requireCurrentReviewerConferenceSeq(adminSeq.longValue());
                session.setAttribute("reviewerConferenceSeq", resolvedConferenceSeq);
            } else {
                resolvedConferenceSeq = conferenceSeq == null
                        ? conferenceSettingsService.getLatestConferenceSeq()
                        : conferenceSeq;
            }
            AdminResponse admin = adminService.getAdmin(resolvedConferenceSeq, adminSeq.longValue());
            if (!"active".equals(admin.getStatus())
                    || (!("admin".equals(admin.getRole())) && !("reviewer".equals(admin.getRole()))
                    && !("maintenance".equals(admin.getRole())))) {
                session.invalidate();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            session.setAttribute("adminRole", admin.getRole());
            return ResponseEntity.ok(AdminLoginResponse.builder()
                    .seq(admin.getSeq())
                    .email(admin.getEmail())
                    .adminName(admin.getAdminName())
                    .role(admin.getRole())
                    .conferenceSeq("reviewer".equals(admin.getRole()) ? resolvedConferenceSeq : null)
                    .build());
        } catch (IllegalArgumentException exception) {
            session.invalidate();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/accounts/page")
    public ResponseEntity<AdminPageResponse> getAdminPage(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return ResponseEntity.ok(adminService.findPage(conferenceSeq, page, size, keyword));
    }

    @GetMapping("/accounts/check-id")
    public ResponseEntity<?> checkAdminId(@RequestParam String email) {
        try {
            return ResponseEntity.ok(adminService.isAdminIdAvailable(email));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/accounts/test-reviewers")
    public ResponseEntity<?> createTestReviewers(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        try {
            TestReviewerCreateResponse response = adminService.createTestReviewerAccounts(conferenceSeq);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("심사자 테스트 계정 생성 중 오류가 발생했습니다.");
        }
    }

    @PostMapping(value = "/accounts/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importAdmins(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            AdminBulkImportResponse response = adminBulkImportService.importAccounts(conferenceSeq, file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("관리자 계정 일괄등록 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/reviewer-categories")
    public ResponseEntity<List<AbstractCategoryResponse>> getReviewerCategories() {
        return ResponseEntity.ok(adminService.getReviewerCategories());
    }

    @PostMapping("/accounts/excel")
    public ResponseEntity<?> downloadExcel(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody AdminAccountExcelDownloadRequest request,
            HttpSession session,
            HttpServletRequest servletRequest
    ) {
        try {
            AuditedExcelExportResult audited = excelDownloadAuditService.execute(
                    ExcelExportType.ADMIN_ACCOUNTS,
                    ((Number) session.getAttribute("adminSeq")).longValue(),
                    request.getReason(),
                    request.auditFilters(),
                    servletRequest,
                    () -> adminService.createAdminsXlsx(conferenceSeq, request.getKeyword())
            );
            return excelResponse(audited);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            log.error("Failed to export admin accounts", exception);
            return ResponseEntity.internalServerError().body("엑셀 파일을 생성하지 못했습니다.");
        }
    }

    private ResponseEntity<byte[]> excelResponse(AuditedExcelExportResult audited) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(audited.exportResult().filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Excel-Download-Log-Id", String.valueOf(audited.logSeq()))
                .contentLength(audited.exportResult().content().length)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(audited.exportResult().content());
    }

    /**
     * 관리자 정보 조회
     * GET /api/admin/accounts/{seq}
     */
    @GetMapping("/accounts/{seq}")
    public ResponseEntity<?> getAdmin(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        try {
            AdminResponse response = adminService.getAdmin(conferenceSeq, seq);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("오류가 발생했습니다.");
        }
    }

    /**
     * 활성 관리자 목록 조회
     * GET /api/admin/accounts/active
     */
    @GetMapping("/accounts/active")
    public ResponseEntity<?> getActiveAdmins(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        try {
            List<AdminResponse> response = adminService.getAllActiveAdmins(conferenceSeq);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("오류가 발생했습니다.");
        }
    }

    /**
     * 모든 관리자 목록 조회
     * GET /api/admin/accounts
     */
    @GetMapping("/accounts")
    public ResponseEntity<?> getAllAdmins(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        try {
            List<AdminResponse> response = adminService.getAllAdmins(conferenceSeq);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("오류가 발생했습니다.");
        }
    }

    /**
     * 관리자 정보 업데이트
     * PUT /api/admin/accounts/{seq}
     */
    @PutMapping("/accounts/{seq}")
    public ResponseEntity<?> updateAdmin(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam(required = false) String adminName,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String affiliation,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String positionTitle,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String contactEmail,
            @RequestParam(required = false) String isUsed,
            @RequestParam(required = false) List<String> expertiseCodes) {
        try {
            AdminResponse response = adminService.updateAdmin(
                    conferenceSeq, seq, adminName, role, status,
                    affiliation, department, positionTitle, phoneNumber, contactEmail,
                    reviewerProfileRequest(
                            affiliation, department, positionTitle, phoneNumber,
                            contactEmail, isUsed, expertiseCodes));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("업데이트 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("/accounts/{seq}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long seq, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("adminSeq") instanceof Long actorSeq)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("관리자 로그인이 필요합니다.");
        }
        if (!com.bjworld21.conference.config.AdminRolePolicy.isFullAdministrator(session.getAttribute("adminRole"))
                || seq.equals(actorSeq)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("비밀번호 초기화는 다른 관리자가 수행해야 합니다.");
        }
        try {
            String newPassword = adminService.resetPassword(seq);
            return ResponseEntity.ok(newPassword);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("비밀번호 초기화 중 오류가 발생했습니다.");
        }
    }

    /**
     * 관리자 삭제
     * DELETE /api/admin/accounts/{seq}
     */
    @DeleteMapping("/accounts/{seq}")
    public ResponseEntity<?> deleteAdmin(@PathVariable Long seq) {
        try {
            adminService.deleteAdmin(seq);
            return ResponseEntity.ok("관리자 계정이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("삭제 중 오류가 발생했습니다.");
        }
    }

    /**
     * 비밀번호 변경
     * POST /api/admin/change-password/{seq}
     */
    @PostMapping("/change-password/{seq}")
    public ResponseEntity<?> changePassword(
            @PathVariable Long seq,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        try {
            adminService.changePassword(seq, oldPassword, newPassword);
            return ResponseEntity.ok("비밀번호가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("비밀번호 변경 중 오류가 발생했습니다.");
        }
    }

    private ReviewerProfileRequest reviewerProfileRequest(
            String affiliation,
            String department,
            String positionTitle,
            String phoneNumber,
            String contactEmail,
            String isUsed,
            List<String> expertiseCodes
    ) {
        return ReviewerProfileRequest.builder()
                .affiliation(affiliation)
                .department(department)
                .positionTitle(positionTitle)
                .phoneNumber(phoneNumber)
                .contactEmail(contactEmail)
                .isUsed(isUsed)
                .expertiseCodes(expertiseCodes)
                .build();
    }
}

