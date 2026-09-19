package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.MemberPageResponse;
import com.bjworld21.conference.dto.MemberDetailResponse;
import com.bjworld21.conference.dto.MemberRegisterRequest;
import com.bjworld21.conference.dto.MemberRegisterResponse;
import com.bjworld21.conference.dto.MemberExcelDownloadRequest;
import com.bjworld21.conference.service.AuditedExcelExportResult;
import com.bjworld21.conference.service.ExcelDownloadAuditService;
import com.bjworld21.conference.service.ExcelExportType;
import com.bjworld21.conference.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/members")
public class MemberController {

    @Autowired
    private MemberService memberService;

    @Autowired
    private ExcelDownloadAuditService excelDownloadAuditService;

    @GetMapping
    public ResponseEntity<MemberPageResponse> list(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String memberType,
            @RequestParam(required = false) Boolean hasPreRegistration,
            @RequestParam(required = false) Boolean hasAbstractSubmission
    ) {
        return ResponseEntity.ok(memberService.findPage(
                conferenceSeq,
                page,
                size,
                keyword,
                memberType,
                hasPreRegistration,
                hasAbstractSubmission
        ));
    }

    @GetMapping("/{seq}")
    public ResponseEntity<?> detail(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        try {
            MemberDetailResponse response = memberService.findDetail(conferenceSeq, seq);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/excel")
    public ResponseEntity<?> downloadExcel(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody MemberExcelDownloadRequest request,
            HttpSession session,
            HttpServletRequest servletRequest
    ) {
        try {
            AuditedExcelExportResult audited = excelDownloadAuditService.execute(
                    ExcelExportType.MEMBERS,
                    ((Number) session.getAttribute("adminSeq")).longValue(),
                    request.getReason(),
                    request.auditFilters(),
                    servletRequest,
                    () -> memberService.createMembersXlsx(
                            conferenceSeq,
                            request.getKeyword(),
                            request.getMemberType(),
                            request.getHasPreRegistration(),
                            request.getHasAbstractSubmission()
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

    @PostMapping("/dummy")
    public ResponseEntity<?> createDummyMembers(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "100") Integer count
    ) {
        try {
            int insertedCount = memberService.createDummyMembers(conferenceSeq, count);
            return ResponseEntity.ok("Dummy members created: " + insertedCount);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while creating dummy members");
        }
    }

    @PutMapping("/{seq}")
    public ResponseEntity<?> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam String memberType,
            @RequestParam String email,
            @RequestParam(required = false) String password,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String institution,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String positionTitle,
            @RequestParam(required = false) String country,
            @RequestParam String mobile,
            @RequestParam(defaultValue = "false") Boolean newsletter
    ) {
        try {
            MemberRegisterRequest request = MemberRegisterRequest.builder()
                    .memberType(memberType)
                    .email(email)
                    .password(password)
                    .firstName(firstName)
                    .lastName(lastName)
                    .institution(institution)
                    .department(department)
                    .positionTitle(positionTitle)
                    .country(country)
                    .mobile(mobile)
                    .newsletter(newsletter)
                    .build();

            MemberRegisterResponse response = memberService.update(conferenceSeq, seq, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while updating member");
        }
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq
    ) {
        try {
            memberService.delete(conferenceSeq, seq);
            return ResponseEntity.ok("Member deleted successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while deleting member");
        }
    }
}

