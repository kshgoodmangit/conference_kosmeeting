package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.BoardAttachmentResponse;
import com.bjworld21.congress.dto.BoardCategoryResponse;
import com.bjworld21.congress.dto.BoardPostPageResponse;
import com.bjworld21.congress.dto.BoardPostRequest;
import com.bjworld21.congress.dto.BoardPostResponse;
import com.bjworld21.congress.service.BoardPostService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/boards")
public class BoardPostController {
    private final BoardPostService service;

    public BoardPostController(BoardPostService service) {
        this.service = service;
    }

    @GetMapping("/{boardSeq}/posts")
    public BoardPostPageResponse list(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long boardSeq,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status
    ) {
        return service.findPage(conferenceSeq, boardSeq, page, size, keyword, status);
    }

    @GetMapping("/{boardSeq}/posts/{seq}")
    public BoardPostResponse get(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                 @PathVariable Long boardSeq, @PathVariable Long seq) {
        return service.get(conferenceSeq, boardSeq, seq);
    }

    @PostMapping("/{boardSeq}/posts")
    public ResponseEntity<BoardPostResponse> create(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long boardSeq,
            @RequestBody BoardPostRequest request,
            HttpSession session
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(conferenceSeq, boardSeq, request, adminSeq(session)));
    }

    @PutMapping("/{boardSeq}/posts/{seq}")
    public BoardPostResponse update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long boardSeq,
            @PathVariable Long seq,
            @RequestBody BoardPostRequest request,
            HttpSession session
    ) {
        return service.update(conferenceSeq, boardSeq, seq, request, adminSeq(session));
    }

    @DeleteMapping("/{boardSeq}/posts/{seq}")
    public ResponseEntity<Void> delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                       @PathVariable Long boardSeq, @PathVariable Long seq) {
        service.delete(conferenceSeq, boardSeq, seq);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/faq-categories")
    public List<BoardCategoryResponse> faqCategories() {
        return service.findFaqCategories();
    }

    @PostMapping(
            value = "/{boardSeq}/posts/{seq}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<BoardAttachmentResponse> uploadAttachment(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long boardSeq,
            @PathVariable Long seq,
            @RequestPart("file") MultipartFile file,
            HttpSession session
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addAttachment(conferenceSeq, boardSeq, seq, file, adminSeq(session)));
    }

    @GetMapping("/{boardSeq}/posts/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<Resource> downloadAttachment(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long boardSeq,
            @PathVariable Long seq,
            @PathVariable Long attachmentSeq
    ) {
        BoardPostService.AttachmentDownload download = service.downloadAttachment(conferenceSeq, boardSeq, seq, attachmentSeq);
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

    @DeleteMapping("/{boardSeq}/posts/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<Void> deleteAttachment(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long boardSeq,
            @PathVariable Long seq,
            @PathVariable Long attachmentSeq
    ) {
        service.deleteAttachment(conferenceSeq, boardSeq, seq, attachmentSeq);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleServerError(IllegalStateException exception) {
        return exception.getMessage();
    }

    private Long adminSeq(HttpSession session) {
        Object value = session.getAttribute("adminSeq");
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("관리자 로그인이 필요합니다.");
        }
        return number.longValue();
    }
}
