package com.bjworld21.congress.controller;

import com.bjworld21.congress.publicsite.PublicApiRequest;

import com.bjworld21.congress.config.IpAccessExempt;

import com.bjworld21.congress.dto.PopupPageResponse;
import com.bjworld21.congress.dto.PopupResponse;
import com.bjworld21.congress.service.PopupService;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.bjworld21.congress.service.WebRiskUnavailableException;
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
public class PopupController {
    private final PopupService popupService;
    private final ConferenceSettingsService conferenceSettingsService;

    public PopupController(PopupService popupService, ConferenceSettingsService conferenceSettingsService) {
        this.popupService = popupService;
        this.conferenceSettingsService = conferenceSettingsService;
    }

    @GetMapping("/admin/popups")
    public ResponseEntity<PopupPageResponse> list(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return ResponseEntity.ok(popupService.findPage(conferenceSeq, page, size, keyword));
    }

    @GetMapping("/admin/popups/{seq}")
    public ResponseEntity<?> getBySeq(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        try {
            return ResponseEntity.ok(popupService.getBySeq(conferenceSeq, seq));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping(value = "/admin/popups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam String title,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(defaultValue = "false") Boolean enabled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useEndDate,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) MultipartFile imageFile
    ) {
        try {
            PopupResponse response = popupService.create(conferenceSeq, title, linkUrl, enabled, useStartDate, useEndDate, content, imageFile);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (WebRiskUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("팝업 생성 중 오류가 발생했습니다.");
        }
    }

    @PutMapping(value = "/admin/popups/{seq}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam String title,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(defaultValue = "false") Boolean enabled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useEndDate,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) MultipartFile imageFile
    ) {
        try {
            return ResponseEntity.ok(popupService.update(conferenceSeq, seq, title, linkUrl, enabled, useStartDate, useEndDate, content, imageFile));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (WebRiskUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("팝업 수정 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/admin/popups/{seq}")
    public ResponseEntity<?> delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        try {
            popupService.delete(conferenceSeq, seq);
            return ResponseEntity.ok("팝업을 삭제했습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("팝업 삭제 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/public/{conferenceSeq}/popups/{seq}/image")
    @IpAccessExempt
    public ResponseEntity<?> image(@PathVariable Long seq) {
        try {
            Resource resource = popupService.getImage(PublicApiRequest.context().conferenceSeq(), seq);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                    .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("팝업 이미지 조회 중 오류가 발생했습니다.");
        }
    }
}
