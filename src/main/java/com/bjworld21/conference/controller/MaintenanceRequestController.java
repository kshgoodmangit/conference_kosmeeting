package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.MaintenanceRequestPageResponse;
import com.bjworld21.conference.dto.MaintenanceRequestResponse;
import com.bjworld21.conference.service.MaintenanceRequestService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/maintenance/requests")
public class MaintenanceRequestController {
    private final MaintenanceRequestService service;

    public MaintenanceRequestController(MaintenanceRequestService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> findPage(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status
    ) {
        try {
            MaintenanceRequestPageResponse response = service.findPage(conferenceSeq, page, size, keyword, status);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @GetMapping("/{seq}")
    public ResponseEntity<?> get(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) {
        try {
            return ResponseEntity.ok(service.get(conferenceSeq, seq));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(required = false) List<MultipartFile> files,
            HttpSession session
    ) {
        try {
            MaintenanceRequestResponse response = service.create(
                    conferenceSeq, adminSeq(session), title, content, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body("유지보수 요청을 등록하지 못했습니다.");
        }
    }

    @PutMapping(path = "/{seq}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(required = false) List<MultipartFile> files,
            HttpSession session
    ) {
        try {
            return ResponseEntity.ok(service.update(
                    conferenceSeq, adminSeq(session), seq, title, content, files));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body("유지보수 요청을 수정하지 못했습니다.");
        }
    }

    @PutMapping(path = "/{seq}/answer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveAnswer(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam String status,
            @RequestParam(defaultValue = "") String answerContent,
            @RequestParam(required = false) List<MultipartFile> files,
            HttpSession session
    ) {
        try {
            return ResponseEntity.ok(service.saveAnswer(
                    conferenceSeq, adminSeq(session), seq, status, answerContent, files));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body("처리 결과를 저장하지 못했습니다.");
        }
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            HttpSession session
    ) {
        try {
            service.delete(conferenceSeq, adminSeq(session), seq);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @GetMapping("/attachments/{attachmentSeq}")
    public ResponseEntity<?> download(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long attachmentSeq
    ) {
        try {
            MaintenanceRequestService.DownloadFile file = service.getAttachment(conferenceSeq, attachmentSeq);
            MediaType mediaType;
            try {
                mediaType = file.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(file.contentType());
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(file.originalFilename(), StandardCharsets.UTF_8).build().toString())
                    .body(new FileSystemResource(file.path()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        }
    }

    private Long adminSeq(HttpSession session) {
        return ((Number) session.getAttribute("adminSeq")).longValue();
    }
}
