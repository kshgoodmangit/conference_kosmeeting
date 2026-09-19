package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.publicsite.PublicApiRequest;
import com.bjworld21.conference.dto.AbstractSubmissionRequest;
import com.bjworld21.conference.entity.AbstractSubmissionAttachment;
import com.bjworld21.conference.service.AbstractPresentationAttachmentService;
import com.bjworld21.conference.service.AbstractSubmissionService;
import com.bjworld21.conference.service.ConferenceSettingsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.StringTokenizer;

/**
 * Member-owned abstract API used by the public submission, review, and material-upload screens.
 * Every item operation delegates the ownership check to the service; do not replace it with a
 * plain sequence lookup.
 */
@IpAccessExempt
@RestController
@RequestMapping("/api/public/{conferenceSeq}/abstracts")
public class PublicAbstractController {
    private static final int MAX_ABSTRACT_WORDS = 300;

    private final AbstractSubmissionService abstractSubmissionService;
    private final AbstractPresentationAttachmentService abstractPresentationAttachmentService;
    private final ConferenceSettingsService conferenceSettingsService;

    public PublicAbstractController(
            AbstractSubmissionService abstractSubmissionService,
            AbstractPresentationAttachmentService abstractPresentationAttachmentService,
            ConferenceSettingsService conferenceSettingsService
    ) {
        this.abstractSubmissionService = abstractSubmissionService;
        this.abstractPresentationAttachmentService = abstractPresentationAttachmentService;
        this.conferenceSettingsService = conferenceSettingsService;
    }

    @GetMapping("/meta")
    public ResponseEntity<?> meta() {
        try {
            return ResponseEntity.ok(abstractSubmissionService.getMeta());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Abstract form options could not be loaded.");
        }
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        try {
            return ResponseEntity.ok(abstractSubmissionService.findByMember(
                    member.conferenceSeq(), member.memberSeq()
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Abstract submissions could not be loaded.");
        }
    }

    @GetMapping("/{seq}")
    public ResponseEntity<?> get(@PathVariable Long seq, HttpSession session) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        try {
            return ResponseEntity.ok(abstractSubmissionService.getByMember(
                    member.conferenceSeq(), seq, member.memberSeq()
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Abstract submission could not be loaded.");
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody AbstractSubmissionRequest request, HttpSession session) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }

        try {
            if (request == null) {
                throw new IllegalArgumentException("Abstract submission is required.");
            }
            if (wordCount(request) > MAX_ABSTRACT_WORDS) {
                throw new IllegalArgumentException("Abstract content must be 300 words or less.");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(abstractSubmissionService.createByMember(
                    member.conferenceSeq(), request, member.memberSeq()
            ));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Abstract submission failed.");
        }
    }

    @PutMapping("/{seq}")
    public ResponseEntity<?> update(
            @PathVariable Long seq,
            @RequestBody AbstractSubmissionRequest request,
            HttpSession session
    ) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        try {
            if (request == null) {
                throw new IllegalArgumentException("Abstract submission is required.");
            }
            if (wordCount(request) > MAX_ABSTRACT_WORDS) {
                throw new IllegalArgumentException("Abstract content must be 300 words or less.");
            }
            return ResponseEntity.ok(abstractSubmissionService.updateByMember(
                    member.conferenceSeq(), seq, member.memberSeq(), request
            ));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Abstract update failed.");
        }
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<?> deleteDraft(@PathVariable Long seq, HttpSession session) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        try {
            // This public route never calls the administrator delete flow; the service enforces owner + draft.
            abstractSubmissionService.deleteDraftByMember(
                    member.conferenceSeq(), seq, member.memberSeq()
            );
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Draft abstract could not be deleted.");
        }
    }

    @PostMapping(value = "/{seq}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPresentationAttachment(
            @PathVariable Long seq,
            @RequestParam("file") MultipartFile file,
            HttpSession session
    ) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(abstractPresentationAttachmentService.addByMember(
                    member.conferenceSeq(), seq, member.memberSeq(), file
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Presentation material could not be uploaded.");
        }
    }

    @GetMapping("/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<?> downloadPresentationAttachment(
            @PathVariable Long seq,
            @PathVariable Long attachmentSeq,
            HttpSession session
    ) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        try {
            long conferenceSeq = member.conferenceSeq();
            abstractSubmissionService.getByMember(conferenceSeq, seq, member.memberSeq());
            AbstractSubmissionAttachment attachment = abstractSubmissionService.requireAttachment(
                    conferenceSeq, seq, attachmentSeq
            );
            Resource resource = abstractSubmissionService.getAttachmentResource(attachment);
            boolean isPdf = "pdf".equalsIgnoreCase(attachment.getFileExtension())
                    || MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(attachment.getContentType());
            ContentDisposition disposition = (isPdf ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8)
                    .build();
            MediaType contentType;
            try {
                contentType = MediaType.parseMediaType(attachment.getContentType());
            } catch (Exception ignored) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(contentType)
                    .contentLength(attachment.getFileSize())
                    .body(resource);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Presentation material could not be downloaded.");
        }
    }

    @DeleteMapping("/{seq}/attachments/{attachmentSeq}")
    public ResponseEntity<?> deletePresentationAttachment(
            @PathVariable Long seq,
            @PathVariable Long attachmentSeq,
            HttpSession session
    ) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        try {
            abstractPresentationAttachmentService.deleteByMember(
                    member.conferenceSeq(), seq, attachmentSeq, member.memberSeq()
            );
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Presentation material could not be deleted.");
        }
    }

    private PublicMemberSession memberSession(HttpSession session) {
        return PublicMemberSession.resolve(session, () -> PublicApiRequest.context().conferenceSeq());
    }

    private int wordCount(AbstractSubmissionRequest request) {
        // Keep this server-side count aligned with user.js; the server remains authoritative.
        StringJoiner content = new StringJoiner(" ");
        add(content, request.getObjectiveText());
        add(content, request.getMethodsText());
        add(content, request.getResultsText());
        add(content, request.getConclusionsText());
        return new StringTokenizer(content.toString()).countTokens();
    }

    private void add(StringJoiner content, String value) {
        if (value != null && !value.isBlank()) {
            content.add(value.trim());
        }
    }
}
