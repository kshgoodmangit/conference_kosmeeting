package com.bjworld21.conference.controller;

import com.bjworld21.conference.publicsite.PublicApiRequest;

import com.bjworld21.conference.dto.SponsorPageResponse;
import com.bjworld21.conference.dto.SponsorResponse;
import com.bjworld21.conference.dto.SponsorTypeResponse;
import com.bjworld21.conference.service.SponsorService;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class SponsorController {
    private final SponsorService sponsorService;
    private final ConferenceSettingsService conferenceSettingsService;

    public SponsorController(SponsorService sponsorService, ConferenceSettingsService conferenceSettingsService) {
        this.sponsorService = sponsorService;
        this.conferenceSettingsService = conferenceSettingsService;
    }

    @GetMapping("/admin/sponsors")
    public ResponseEntity<SponsorPageResponse> list(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return ResponseEntity.ok(sponsorService.findPage(conferenceSeq, page, size, keyword));
    }

    @GetMapping("/admin/sponsors/types")
    public ResponseEntity<List<SponsorTypeResponse>> types() {
        return ResponseEntity.ok(sponsorService.findSponsorTypes());
    }

    @GetMapping("/admin/sponsors/{seq}")
    public ResponseEntity<?> getBySeq(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        try {
            return ResponseEntity.ok(sponsorService.getBySeq(conferenceSeq, seq));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        }
    }

    @PostMapping(value = "/admin/sponsors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam Long sponsorTypeCode,
            @RequestParam String sponsorName,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useEndDate,
            @RequestParam(defaultValue = "true") Boolean enabled,
            @RequestParam(defaultValue = "0") Integer sortOrder,
            @RequestParam MultipartFile logoFile
    ) {
        try {
            SponsorResponse response = sponsorService.create(
                    conferenceSeq,
                    sponsorTypeCode,
                    sponsorName,
                    linkUrl,
                    useStartDate,
                    useEndDate,
                    enabled,
                    sortOrder,
                    logoFile
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("스폰서 등록 중 오류가 발생했습니다.");
        }
    }

    @PutMapping(value = "/admin/sponsors/{seq}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam Long sponsorTypeCode,
            @RequestParam String sponsorName,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useEndDate,
            @RequestParam(defaultValue = "true") Boolean enabled,
            @RequestParam(defaultValue = "0") Integer sortOrder,
            @RequestParam(required = false) MultipartFile logoFile
    ) {
        try {
            return ResponseEntity.ok(sponsorService.update(
                    conferenceSeq,
                    seq,
                    sponsorTypeCode,
                    sponsorName,
                    linkUrl,
                    useStartDate,
                    useEndDate,
                    enabled,
                    sortOrder,
                    logoFile
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("스폰서 수정 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/admin/sponsors/{seq}")
    public ResponseEntity<?> delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        try {
            sponsorService.delete(conferenceSeq, seq);
            return ResponseEntity.ok("스폰서를 삭제했습니다.");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("스폰서 삭제 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/public/{conferenceSeq}/sponsors/{seq}/logo")
    @com.bjworld21.conference.config.IpAccessExempt
    public ResponseEntity<?> logo(@PathVariable Long seq) {
        try {
            Resource resource = sponsorService.getLogo(PublicApiRequest.context().conferenceSeq(), seq);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                    .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .body(resource);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("스폰서 로고 조회 중 오류가 발생했습니다.");
        }
    }
}
