package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.MailHistoryData.*;
import com.bjworld21.conference.service.MailCampaignService;
import com.bjworld21.conference.service.MailHistoryService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataAccessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/mail-history")
public class MailHistoryController {
    private final MailHistoryService service;
    private final MailCampaignService campaigns;
    public MailHistoryController(MailHistoryService service, MailCampaignService campaigns) { this.service = service; this.campaigns = campaigns; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Saved save(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @Valid @RequestPart("request") SaveRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, HttpSession session) {
        return service.save(conferenceSeq, ((Number) session.getAttribute("adminSeq")).longValue(), request, files);
    }

    @GetMapping
    public Page page(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String sourceMenu,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "") String sourceType, @RequestParam(required = false) Long sourceSeq) {
        Filter filter = new Filter(); filter.setConferenceSeq(conferenceSeq); filter.setKeyword(keyword);
        filter.setSourceMenu(sourceMenu); filter.setStatus(status); filter.setDateFrom(dateFrom); filter.setDateTo(dateTo);
        filter.setSourceType(sourceType); filter.setSourceSeq(sourceSeq);
        return service.page(filter, page, size);
    }

    @GetMapping("/{seq}")
    public Detail detail(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        return service.detail(conferenceSeq, seq);
    }

    @GetMapping("/emails")
    public EmailPage emails(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String sourceMenu,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return service.emails(emailFilter(conferenceSeq, keyword, sourceMenu, status, dateFrom, dateTo), page, size);
    }

    @GetMapping("/emails/{recipientSeq}")
    public EmailHistoryPage emailHistories(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long recipientSeq,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String sourceMenu,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return service.emailHistories(emailFilter(conferenceSeq, keyword, sourceMenu, status, dateFrom, dateTo), recipientSeq, page, size);
    }

    private Filter emailFilter(Long conferenceSeq, String keyword, String sourceMenu, String status, LocalDate dateFrom, LocalDate dateTo) {
        Filter filter = new Filter(); filter.setConferenceSeq(conferenceSeq); filter.setKeyword(keyword);
        filter.setSourceMenu(sourceMenu); filter.setStatus(status); filter.setDateFrom(dateFrom); filter.setDateTo(dateTo);
        return filter;
    }

    @GetMapping("/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<Resource> attachment(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq, @PathVariable Long attachmentSeq) {
        Long campaignSeq = service.requireHistory(conferenceSeq, seq).getCampaignSeq();
        var file = campaigns.requireAttachment(conferenceSeq, campaignSeq, attachmentSeq);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .contentLength(file.getFileSize()).body(campaigns.getAttachment(conferenceSeq, campaignSeq, attachmentSeq));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> storageFailure(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("메일 이력 저장소에 연결할 수 없습니다. DB 변경 적용 여부와 연결 상태를 확인해 주세요.");
    }
}
