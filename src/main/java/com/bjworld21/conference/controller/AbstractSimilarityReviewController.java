package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.AdminRolePolicy;
import com.bjworld21.conference.config.LicenseProperties;
import com.bjworld21.conference.dto.AbstractSimilarityReviewUpdateRequest;
import com.bjworld21.conference.service.AbstractSimilarityReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/abstract-similarity-reviews")
public class AbstractSimilarityReviewController {
    private final AbstractSimilarityReviewService service;
    private final LicenseProperties licenseProperties;

    public AbstractSimilarityReviewController(
            AbstractSimilarityReviewService service,
            LicenseProperties licenseProperties
    ) {
        this.service = service;
        this.licenseProperties = licenseProperties;
    }

    @GetMapping
    public ResponseEntity<?> findPage(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String riskLevel,
            @RequestParam(required = false) Boolean stale,
            HttpSession session
    ) {
        ResponseEntity<String> denied = requireAccess(session);
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(service.findPage(
                    conferenceSeq, page, size, keyword, status, riskLevel, stale
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
        }
    }

    @GetMapping("/{abstractSeq}/history")
    public ResponseEntity<?> findHistory(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long abstractSeq,
            HttpSession session
    ) {
        ResponseEntity<String> denied = requireAccess(session);
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(service.findHistory(conferenceSeq, abstractSeq));
        } catch (AbstractSimilarityReviewService.SimilarityReviewNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PutMapping("/{abstractSeq}")
    public ResponseEntity<?> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long abstractSeq,
            @RequestBody AbstractSimilarityReviewUpdateRequest request,
            HttpSession session
    ) {
        ResponseEntity<String> denied = requireAccess(session);
        if (denied != null) return denied;
        try {
            Long adminSeq = ((Number) session.getAttribute("adminSeq")).longValue();
            return ResponseEntity.ok(service.update(conferenceSeq, abstractSeq, request, adminSeq));
        } catch (AbstractSimilarityReviewService.SimilarityReviewNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
        }
    }

    private ResponseEntity<String> requireAccess(HttpSession session) {
        if (session == null || session.getAttribute("adminSeq") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("관리자 로그인이 필요합니다.");
        }
        if (!AdminRolePolicy.isFullAdministrator(session.getAttribute("adminRole"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자 권한이 필요합니다.");
        }
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
        return null;
    }
}
