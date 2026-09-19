package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.SponsorshipApplicationPageResponse;
import com.bjworld21.conference.entity.SponsorshipApplication;
import com.bjworld21.conference.service.SponsorshipApplicationService;
import com.bjworld21.conference.service.ConferenceSettingsService;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class SponsorshipApplicationController {
    private final SponsorshipApplicationService sponsorshipApplicationService;
    private final ConferenceSettingsService conferenceSettingsService;

    public SponsorshipApplicationController(SponsorshipApplicationService sponsorshipApplicationService,
                                            ConferenceSettingsService conferenceSettingsService) {
        this.sponsorshipApplicationService = sponsorshipApplicationService;
        this.conferenceSettingsService = conferenceSettingsService;
    }

    @GetMapping("/admin/sponsorship-applications")
    public ResponseEntity<SponsorshipApplicationPageResponse> list(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return ResponseEntity.ok(sponsorshipApplicationService.findPage(conferenceSeq, page, size, keyword));
    }

    @GetMapping("/admin/sponsorship-applications/{seq}")
    public ResponseEntity<?> getBySeq(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        try {
            return ResponseEntity.ok(sponsorshipApplicationService.getBySeq(conferenceSeq, seq));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping(
            value = "/admin/sponsorship-applications",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> create(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam String companyKrName,
            @RequestParam(required = false) String companyEnName,
            @RequestParam String ceoName,
            @RequestParam String businessNumber,
            @RequestParam String zonecode,
            @RequestParam String address,
            @RequestParam String addressDetail,
            @RequestParam String sponsorshipType,
            @RequestParam(defaultValue = "0") Long sponsorshipAmount,
            @RequestParam String contactPersonName,
            @RequestParam(required = false) String contactPersonPosition,
            @RequestParam(required = false) String contactPersonDepartment,
            @RequestParam String contactPersonPhone,
            @RequestParam String contactPersonMobile,
            @RequestParam String contactPersonEmail,
            @RequestParam(required = false) String faxNumber,
            @RequestParam(defaultValue = "false") Boolean isDeposited,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depositDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDepositDate,
            @RequestParam(required = false) String taxInvoiceRecipient,
            @RequestParam(required = false) String taxInvoiceEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate taxInvoiceIssueDate,
            @RequestParam(required = false) String taxInvoiceType,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) MultipartFile businessLicenseFile
    ) {
        try {
            SponsorshipApplication application = buildApplication(companyKrName, companyEnName, ceoName, businessNumber, zonecode,
                    address, addressDetail, sponsorshipType, sponsorshipAmount, contactPersonName, contactPersonPosition,
                    contactPersonDepartment, contactPersonPhone, contactPersonMobile, contactPersonEmail, faxNumber,
                    isDeposited, depositDate, expectedDepositDate, taxInvoiceRecipient, taxInvoiceEmail, taxInvoiceIssueDate,
                    taxInvoiceType, remarks);
            Long resolvedConferenceSeq = conferenceSeq;
            return ResponseEntity.status(HttpStatus.CREATED).body(sponsorshipApplicationService.create(resolvedConferenceSeq, application, businessLicenseFile));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("후원 신청 등록 중 오류가 발생했습니다.");
        }
    }

    @PutMapping(value = "/admin/sponsorship-applications/{seq}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam String companyKrName,
            @RequestParam(required = false) String companyEnName,
            @RequestParam String ceoName,
            @RequestParam String businessNumber,
            @RequestParam String zonecode,
            @RequestParam String address,
            @RequestParam String addressDetail,
            @RequestParam String sponsorshipType,
            @RequestParam(defaultValue = "0") Long sponsorshipAmount,
            @RequestParam String contactPersonName,
            @RequestParam(required = false) String contactPersonPosition,
            @RequestParam(required = false) String contactPersonDepartment,
            @RequestParam String contactPersonPhone,
            @RequestParam String contactPersonMobile,
            @RequestParam String contactPersonEmail,
            @RequestParam(required = false) String faxNumber,
            @RequestParam(defaultValue = "false") Boolean isDeposited,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depositDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDepositDate,
            @RequestParam(required = false) String taxInvoiceRecipient,
            @RequestParam(required = false) String taxInvoiceEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate taxInvoiceIssueDate,
            @RequestParam(required = false) String taxInvoiceType,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) MultipartFile businessLicenseFile
    ) {
        try {
            SponsorshipApplication application = buildApplication(companyKrName, companyEnName, ceoName, businessNumber, zonecode,
                    address, addressDetail, sponsorshipType, sponsorshipAmount, contactPersonName, contactPersonPosition,
                    contactPersonDepartment, contactPersonPhone, contactPersonMobile, contactPersonEmail, faxNumber,
                    isDeposited, depositDate, expectedDepositDate, taxInvoiceRecipient, taxInvoiceEmail, taxInvoiceIssueDate,
                    taxInvoiceType, remarks);
            return ResponseEntity.ok(sponsorshipApplicationService.update(conferenceSeq, seq, application, businessLicenseFile));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("후원 신청 수정 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/admin/sponsorship-applications/{seq}")
    public ResponseEntity<?> delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        try {
            sponsorshipApplicationService.delete(conferenceSeq, seq);
            return ResponseEntity.ok("후원 신청이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("후원 신청 삭제 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/admin/sponsorship-applications/{seq}/business-license")
    public ResponseEntity<?> businessLicense(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        try {
            Resource resource = sponsorshipApplicationService.getBusinessLicense(conferenceSeq, seq);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                    .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("사업자등록증 조회 중 오류가 발생했습니다.");
        }
    }

    private SponsorshipApplication buildApplication(
            String companyKrName,
            String companyEnName,
            String ceoName,
            String businessNumber,
            String zonecode,
            String address,
            String addressDetail,
            String sponsorshipType,
            Long sponsorshipAmount,
            String contactPersonName,
            String contactPersonPosition,
            String contactPersonDepartment,
            String contactPersonPhone,
            String contactPersonMobile,
            String contactPersonEmail,
            String faxNumber,
            Boolean isDeposited,
            LocalDate depositDate,
            LocalDate expectedDepositDate,
            String taxInvoiceRecipient,
            String taxInvoiceEmail,
            LocalDate taxInvoiceIssueDate,
            String taxInvoiceType,
            String remarks
    ) {
        return SponsorshipApplication.builder()
                .companyKrName(companyKrName)
                .companyEnName(companyEnName)
                .ceoName(ceoName)
                .businessNumber(businessNumber)
                .zonecode(zonecode)
                .address(address)
                .addressDetail(addressDetail)
                .sponsorshipType(sponsorshipType)
                .sponsorshipAmount(sponsorshipAmount)
                .contactPersonName(contactPersonName)
                .contactPersonPosition(contactPersonPosition)
                .contactPersonDepartment(contactPersonDepartment)
                .contactPersonPhone(contactPersonPhone)
                .contactPersonMobile(contactPersonMobile)
                .contactPersonEmail(contactPersonEmail)
                .faxNumber(faxNumber)
                .isDeposited(isDeposited)
                .depositDate(depositDate)
                .expectedDepositDate(expectedDepositDate)
                .taxInvoiceRecipient(taxInvoiceRecipient)
                .taxInvoiceEmail(taxInvoiceEmail)
                .taxInvoiceIssueDate(taxInvoiceIssueDate)
                .taxInvoiceType(taxInvoiceType)
                .remarks(remarks)
                .build();
    }
}
