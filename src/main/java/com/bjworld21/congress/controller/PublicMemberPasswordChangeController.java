package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.IpAccessExempt;
import com.bjworld21.congress.publicsite.PublicApiRequest;
import com.bjworld21.congress.security.MemberCredentialFingerprint;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.bjworld21.congress.service.MemberPasswordChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@IpAccessExempt
@RestController
@RequestMapping("/api/public/{conferenceSeq}/members/password")
public class PublicMemberPasswordChangeController {
    private static final Logger log = LoggerFactory.getLogger(PublicMemberPasswordChangeController.class);
    private final MemberPasswordChangeService service;
    private final ConferenceSettingsService conferences;

    public PublicMemberPasswordChangeController(MemberPasswordChangeService service, ConferenceSettingsService conferences) {
        this.service = service;
        this.conferences = conferences;
    }

    public record ChangeRequest(String currentPassword, String newPassword, String newPasswordConfirm) {
        @Override public String toString() { return "ChangeRequest[REDACTED]"; }
    }
    public record ErrorResponse(String field, String message) {}

    @PostMapping("/change")
    public ResponseEntity<?> change(@RequestBody ChangeRequest body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        long conferenceSeq = PublicApiRequest.context().conferenceSeq();
        PublicMemberSession member = PublicMemberSession.resolve(session, conferenceSeq);
        String fingerprint = PublicMemberSession.fingerprint(session, conferenceSeq);
        if (member == null || fingerprint == null) {
            PublicMemberSession.signOut(session, conferenceSeq);
            return error(401, null, "Your session has expired. Please sign in again.");
        }
        try {
            service.change(conferenceSeq, member.memberSeq(), fingerprint,
                    body.currentPassword(), body.newPassword(), body.newPasswordConfirm());
        } catch (MemberPasswordChangeService.SessionExpiredException exception) {
            PublicMemberSession.signOut(session, conferenceSeq);
            return error(401, null, "Your session has expired. Please sign in again.");
        }
        PublicMemberSession.signOut(session, conferenceSeq);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @ExceptionHandler(MemberPasswordChangeService.InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> invalid(MemberPasswordChangeService.InvalidPasswordException exception) {
        return error(400, exception.getField(), exception.getMessage());
    }

    @ExceptionHandler(MemberPasswordChangeService.TooManyAttemptsException.class)
    public ResponseEntity<ErrorResponse> limited() {
        return ResponseEntity.status(429).header("Cache-Control", "no-store").header("Retry-After", "3600")
                .body(new ErrorResponse(null, "Too many attempts. Please try again in an hour or use Forgot Password."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformed() {
        return error(400, null, "Please check the password fields.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unavailable(Exception exception) {
        log.error("Password change failed; errorType={}", exception.getClass().getSimpleName());
        return error(503, null, "Password change is temporarily unavailable. Please try again later.");
    }

    private ResponseEntity<ErrorResponse> error(int status, String field, String message) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(new ErrorResponse(field, message));
    }
}
