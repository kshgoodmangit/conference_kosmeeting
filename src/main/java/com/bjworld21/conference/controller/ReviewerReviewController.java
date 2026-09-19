package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.ReviewerReviewSaveRequest;
import com.bjworld21.conference.service.ReviewerReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviewer/reviews")
public class ReviewerReviewController {
    private final ReviewerReviewService reviewerReviewService;

    public ReviewerReviewController(ReviewerReviewService reviewerReviewService) {
        this.reviewerReviewService = reviewerReviewService;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long presentationTypeCode,
            @RequestParam(required = false) Long categoryCode,
            @RequestParam(defaultValue = "") String status,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireReviewerAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            return ResponseEntity.ok(reviewerReviewService.findMyAssignments(
                    conferenceSeq(session), adminSeq(session), keyword, presentationTypeCode, categoryCode, status
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("배정된 초록을 불러오지 못했습니다.");
        }
    }

    @GetMapping("/meta")
    public ResponseEntity<?> getFilterMeta(HttpSession session) {
        ResponseEntity<String> accessDenied = requireReviewerAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            return ResponseEntity.ok(reviewerReviewService.getFilterMeta());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("검색 조건을 불러오지 못했습니다.");
        }
    }

    @GetMapping("/{assignmentSeq}")
    public ResponseEntity<?> detail(@PathVariable Long assignmentSeq, HttpSession session) {
        ResponseEntity<String> accessDenied = requireReviewerAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            return ResponseEntity.ok(reviewerReviewService.getDetail(
                    conferenceSeq(session), adminSeq(session), assignmentSeq
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("심사 정보를 불러오지 못했습니다.");
        }
    }

    @PutMapping("/{assignmentSeq}/draft")
    public ResponseEntity<?> saveDraft(
            @PathVariable Long assignmentSeq,
            @RequestBody ReviewerReviewSaveRequest request,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireReviewerAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            return ResponseEntity.ok(reviewerReviewService.saveDraft(
                    conferenceSeq(session), adminSeq(session), assignmentSeq, request
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("심사 임시저장에 실패했습니다.");
        }
    }

    @PostMapping("/{assignmentSeq}/submit")
    public ResponseEntity<?> submit(
            @PathVariable Long assignmentSeq,
            @RequestBody ReviewerReviewSaveRequest request,
            HttpSession session
    ) {
        ResponseEntity<String> accessDenied = requireReviewerAccess(session);
        if (accessDenied != null) {
            return accessDenied;
        }
        try {
            return ResponseEntity.ok(reviewerReviewService.submit(
                    conferenceSeq(session), adminSeq(session), assignmentSeq, request
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("심사 제출에 실패했습니다.");
        }
    }

    private ResponseEntity<String> requireReviewerAccess(HttpSession session) {
        if (!(session.getAttribute("adminSeq") instanceof Number)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }
        if (!"reviewer".equals(session.getAttribute("adminRole"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("심사자만 접근할 수 있습니다.");
        }
        if (!(session.getAttribute("reviewerConferenceSeq") instanceof Number)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("심사자 학회 정보가 없습니다. 다시 로그인해 주세요.");
        }
        return null;
    }

    private Long adminSeq(HttpSession session) {
        return ((Number) session.getAttribute("adminSeq")).longValue();
    }

    private Long conferenceSeq(HttpSession session) {
        return ((Number) session.getAttribute("reviewerConferenceSeq")).longValue();
    }
}
