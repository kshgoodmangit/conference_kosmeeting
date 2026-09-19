package com.bjworld21.conference.controller;

import com.bjworld21.conference.service.AdminAccessRequestMailLink;
import com.bjworld21.conference.service.AdminAccessRequestService;
import com.bjworld21.conference.service.AdminCredentialVerifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/access-requests/{seq}/mail-approval")
public class AdminAccessRequestMailApprovalController {
    private final AdminAccessRequestMailLink mailLink;
    private final AdminAccessRequestService service;
    private final AdminCredentialVerifier credentials;

    public AdminAccessRequestMailApprovalController(AdminAccessRequestMailLink mailLink,
            AdminAccessRequestService service, AdminCredentialVerifier credentials) {
        this.mailLink = mailLink;
        this.service = service;
        this.credentials = credentials;
    }

    @GetMapping
    public ResponseEntity<?> detail(@PathVariable Long seq, @RequestParam long expires, @RequestParam String signature) {
        mailLink.verify(seq, expires, signature);
        var request = service.findBySeq(seq);
        if (!"REQUESTED".equals(request.getStatus())) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("seq", request.getSeq(), "status", request.getStatus()));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(request);
    }

    @PostMapping
    public ResponseEntity<?> approve(@PathVariable Long seq, @RequestBody ApprovalInput input) {
        mailLink.verify(seq, input.expires(), input.signature());
        var administrator = credentials.verifyActiveAdministrator(input.email(), input.password());
        service.approve(seq, administrator.getSeq());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("message", "접근을 허용했습니다. 요청한 사용기간 동안 관리자 페이지에 접근할 수 있습니다."));
    }

    @ExceptionHandler(AdminCredentialVerifier.AuthenticationRejectedException.class)
    public ResponseEntity<String> authenticationError() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                .body("관리자 인증에 실패했습니다. 계정 정보와 잠금 상태를 확인해 주세요.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> statusError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore()).body(exception.getReason());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> validationError(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(exception.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> storageError() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    public record ApprovalInput(String email, String password, long expires, String signature) {}
}
