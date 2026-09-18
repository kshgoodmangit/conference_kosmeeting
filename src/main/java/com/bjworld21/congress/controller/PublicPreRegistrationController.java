package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.IpAccessExempt;
import com.bjworld21.congress.dto.PublicPreRegistrationData;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.bjworld21.congress.service.PublicPreRegistrationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Signed-in member API for loading, creating, paying, and cancelling one registration. */
@IpAccessExempt
@RestController
@RequestMapping("/api/public/pre-registrations")
public class PublicPreRegistrationController {
    private final PublicPreRegistrationService service;
    private final ConferenceSettingsService conferences;

    public PublicPreRegistrationController(
            PublicPreRegistrationService service,
            ConferenceSettingsService conferences
    ) {
        this.service = service;
        this.conferences = conferences;
    }

    @GetMapping("/form")
    public ResponseEntity<?> form(HttpSession session) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        return ResponseEntity.ok(service.form(member.conferenceSeq(), member.memberSeq()));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody PublicPreRegistrationData.Request request,
            HttpSession session
    ) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(member.conferenceSeq(), member.memberSeq(), request));
    }

    @PutMapping
    public ResponseEntity<?> update(
            @RequestBody PublicPreRegistrationData.Request request,
            HttpSession session
    ) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        return ResponseEntity.ok(service.update(member.conferenceSeq(), member.memberSeq(), request));
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(HttpSession session) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        return ResponseEntity.ok(service.checkout(member.conferenceSeq(), member.memberSeq()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(HttpSession session) {
        PublicMemberSession member = memberSession(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }
        service.cancel(member.conferenceSeq(), member.memberSeq());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    private PublicMemberSession memberSession(HttpSession session) {
        return PublicMemberSession.resolve(session, () -> conferences.getLatestConferenceSeq());
    }
}
