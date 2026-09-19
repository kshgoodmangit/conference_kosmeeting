package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.publicsite.PublicApiRequest;
import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.service.MemberPasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@IpAccessExempt
@RestController
@RequestMapping("/api/public/{conferenceSeq}/members/password-reset")
public class PublicMemberPasswordResetController {
    private static final Logger log = LoggerFactory.getLogger(PublicMemberPasswordResetController.class);
    private final MemberPasswordResetService service;
    private final ClientIpResolver ips;

    public PublicMemberPasswordResetController(MemberPasswordResetService service, ClientIpResolver ips) {
        this.service = service;
        this.ips = ips;
    }

    public record Request(@NotBlank @Email @Size(max = 255) String email) {}
    public record TokenRequest(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {}
    public record ResetRequest(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                              @NotNull @Size(min = 8, max = 16) String password,
                              @NotNull @Size(min = 8, max = 16) String passwordConfirm) {}

    @PostMapping("/request")
    public ResponseEntity<String> request(@Valid @RequestBody Request body, HttpServletRequest request) {
        service.requestReset(body.email(), ips.resolve(request));
        return ResponseEntity.accepted().header("Cache-Control", "no-store")
                .body("If an account matches this email address, a password reset link will be sent. Please check your inbox and spam folder. You can request another link in a minute.");
    }

    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@Valid @RequestBody TokenRequest body, HttpServletRequest request) {
        service.validateToken(body.token(), ips.resolve(request));
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
        service.resetPassword(body.token(), body.password(), body.passwordConfirm(), ips.resolve(request));
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @ExceptionHandler(MemberPasswordResetService.InvalidTokenException.class)
    public ResponseEntity<String> invalidToken() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                .body("This reset link is invalid or has expired. Please request a new link.");
    }

    @ExceptionHandler(MemberPasswordResetService.TooManyAttemptsException.class)
    public ResponseEntity<String> limited() {
        return ResponseEntity.status(429).header("Cache-Control", "no-store").header("Retry-After", "3600")
                .body("Too many attempts. Please try again later.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<String> invalidInput() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                .body("Please check the email address, reset link, and password fields.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> unavailable(Exception exception) {
        log.error("Password recovery request failed; errorType={}", exception.getClass().getSimpleName());
        return ResponseEntity.status(503).header("Cache-Control", "no-store")
                .body("Password recovery is temporarily unavailable. Please try again later.");
    }
}
