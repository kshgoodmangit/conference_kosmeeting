package com.bjworld21.congress.controller;

import com.bjworld21.congress.service.BoardPostService;
import com.bjworld21.congress.service.ConferenceSettingsService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/boards")
public class PublicBoardAttachmentController {
    private final BoardPostService service;
    private final ConferenceSettingsService conferenceSettingsService;

    public PublicBoardAttachmentController(BoardPostService service,
                                           ConferenceSettingsService conferenceSettingsService) {
        this.service = service;
        this.conferenceSettingsService = conferenceSettingsService;
    }

    @GetMapping("/{boardSeq}/posts/{postSeq}/attachments/{attachmentSeq}")
    public ResponseEntity<Resource> download(
            @PathVariable Long boardSeq,
            @PathVariable Long postSeq,
            @PathVariable Long attachmentSeq
    ) {
        BoardPostService.AttachmentDownload download = service.downloadPublicAttachment(
                conferenceSettingsService.getLatestConferenceSeq(),
                boardSeq,
                postSeq,
                attachmentSeq
        );
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.fileSize())
                .body(download.resource());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException exception) {
        return exception.getMessage();
    }
}
