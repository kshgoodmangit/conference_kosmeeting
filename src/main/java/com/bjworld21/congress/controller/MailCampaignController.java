package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.MailCampaignDetailResponse;
import com.bjworld21.congress.dto.MailCampaignPageResponse;
import com.bjworld21.congress.dto.MailCampaignRequest;
import com.bjworld21.congress.dto.MailQueueRequest;
import com.bjworld21.congress.dto.MailQueueResponse;
import com.bjworld21.congress.dto.MailRecipientPreviewResponse;
import com.bjworld21.congress.dto.MailRecipientSelectionRequest;
import com.bjworld21.congress.service.MailRecipientSelectionService;
import com.bjworld21.congress.entity.MailAttachment;
import com.bjworld21.congress.service.MailCampaignService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/mail/campaigns")
public class MailCampaignController {
    private final MailCampaignService service;

    private final MailRecipientSelectionService recipientSelectionService;

    public MailCampaignController(MailCampaignService service, MailRecipientSelectionService recipientSelectionService) {
        this.service = service;
        this.recipientSelectionService = recipientSelectionService;
    }

    @PostMapping("/recipient-preview")
    public MailRecipientPreviewResponse preview(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                                @Valid @RequestBody MailRecipientSelectionRequest request) {
        return recipientSelectionService.preview(conferenceSeq, request);
    }

    @GetMapping
    public MailCampaignPageResponse list(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return service.findPage(conferenceSeq, page, size, keyword);
    }

    @GetMapping("/{seq}")
    public MailCampaignDetailResponse get(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                          @PathVariable Long seq) {
        return service.get(conferenceSeq, seq);
    }

    @PostMapping
    public ResponseEntity<MailCampaignDetailResponse> create(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                                             @Valid @RequestBody MailCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(conferenceSeq, request));
    }

    @PutMapping("/{seq}")
    public MailCampaignDetailResponse update(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                             @PathVariable Long seq, @Valid @RequestBody MailCampaignRequest request) {
        return service.update(conferenceSeq, seq, request);
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<Void> delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                       @PathVariable Long seq) {
        service.delete(conferenceSeq, seq);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{seq}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MailAttachment> uploadAttachment(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addAttachment(conferenceSeq, seq, file));
    }

    @GetMapping("/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<Resource> downloadAttachment(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                                       @PathVariable Long seq, @PathVariable Long attachmentSeq) {
        MailAttachment attachment = service.requireAttachment(conferenceSeq, seq, attachmentSeq);
        Resource resource = service.getAttachment(conferenceSeq, seq, attachmentSeq);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(attachment.getFileSize())
                .body(resource);
    }

    @DeleteMapping("/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<Void> deleteAttachment(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                                 @PathVariable Long seq, @PathVariable Long attachmentSeq) {
        service.deleteAttachment(conferenceSeq, seq, attachmentSeq);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seq}/prepare")
    public MailQueueResponse prepare(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                     @PathVariable Long seq, @Valid @RequestBody MailQueueRequest request) {
        return service.prepareJob(conferenceSeq, seq, request);
    }
}
