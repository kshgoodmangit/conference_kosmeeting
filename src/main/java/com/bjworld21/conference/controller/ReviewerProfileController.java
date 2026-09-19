package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.ReviewerOwnProfileUpdateRequest;
import com.bjworld21.conference.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviewer/profile")
public class ReviewerProfileController {
    private final AdminService adminService;

    public ReviewerProfileController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<?> getOwnProfile(HttpSession session) {
        try {
            return ResponseEntity.ok(adminService.getOwnReviewerProfile(
                    conferenceSeq(session), adminSeq(session)
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(adminService.getReviewerCategories());
    }

    @PutMapping
    public ResponseEntity<?> updateOwnProfile(
            @RequestBody ReviewerOwnProfileUpdateRequest request,
            HttpSession session
    ) {
        try {
            return ResponseEntity.ok(adminService.updateOwnReviewerProfile(
                    conferenceSeq(session), adminSeq(session), request
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Reviewer 정보를 수정하는 중 오류가 발생했습니다.");
        }
    }

    private Long adminSeq(HttpSession session) {
        Object value = session.getAttribute("adminSeq");
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("로그인 정보가 없습니다.");
        }
        return number.longValue();
    }

    private Long conferenceSeq(HttpSession session) {
        Object value = session.getAttribute("reviewerConferenceSeq");
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("로그인한 Reviewer의 학회 정보가 없습니다.");
        }
        return number.longValue();
    }
}
