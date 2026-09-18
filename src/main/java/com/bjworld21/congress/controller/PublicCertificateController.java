package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.IpAccessExempt;
import com.bjworld21.congress.service.ConferenceSettingsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@IpAccessExempt
@RestController
@RequestMapping("/api/public/members")
public class PublicCertificateController {
    private final ConferenceSettingsService conferenceSettingsService;

    public PublicCertificateController(ConferenceSettingsService conferenceSettingsService) {
        this.conferenceSettingsService = conferenceSettingsService;
    }

    @GetMapping("/certificate")
    public ResponseEntity<?> download(HttpSession session) {
        PublicMemberSession member = PublicMemberSession.resolve(
                session,
                () -> conferenceSettingsService.getLatestConferenceSeq()
        );
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required.");
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("certificate.png", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.IMAGE_PNG)
                .body(new ClassPathResource("static/public/img/certificate.png"));
    }
}
