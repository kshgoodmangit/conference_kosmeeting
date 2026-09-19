package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.publicsite.PublicApiRequest;
import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.service.MemberEmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/{conferenceSeq}/members/email-verification")
@IpAccessExempt
public class PublicMemberEmailVerificationController {
    private final MemberEmailVerificationService service;
    private final ClientIpResolver ips;

    public PublicMemberEmailVerificationController(MemberEmailVerificationService service, ClientIpResolver ips) {
        this.service = service;
        this.ips = ips;
    }

    public record SendRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record VerifyRequest(@NotBlank @Email @Size(max = 255) String email,
                                @NotBlank @Size(max = 32) String code) {}

    @PostMapping("/verify")
    public ResponseEntity<MemberEmailVerificationService.VerifyResult> verify(@Valid @RequestBody VerifyRequest body,
                                                                             HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(service.verifyCode(body.email(), body.code(), ips.resolve(request), request.getSession(false)));
    }

    @ExceptionHandler(MemberEmailVerificationService.InvalidCodeException.class)
    public ResponseEntity<String> invalidCode(MemberEmailVerificationService.InvalidCodeException exception) {
        return ResponseEntity.status(exception instanceof MemberEmailVerificationService.IncorrectCodeException ? 400 : 410)
                .header("Cache-Control", "no-store").body(exception.getMessage());
    }

    @ExceptionHandler(MemberEmailVerificationService.VerificationRateLimitException.class)
    public ResponseEntity<String> verificationLimited() {
        return ResponseEntity.status(429).header("Cache-Control", "no-store").header("Retry-After", "3600")
                .body("Too many verification attempts. Please try again in an hour.");
    }

    @PostMapping("/send")
    public ResponseEntity<MemberEmailVerificationService.SendResult> send(@Valid @RequestBody SendRequest body,
                                                                        HttpServletRequest request) throws Exception {
        var result = service.sendCode(body.email(), ips.resolve(request), request.getSession());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }

    @ExceptionHandler(MemberEmailVerificationService.TooManyRequestsException.class)
    public ResponseEntity<String> limited() {
        return ResponseEntity.status(429).header("Cache-Control", "no-store").header("Retry-After", "60")
                .body("Please wait before requesting another code. You can request up to 5 codes per hour.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<String> invalidInput() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body("Please enter a valid email address.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> unavailable(Exception exception) {
        // SMTP errors may contain recipient addresses or credentials. Never include their messages.
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Email verification failed; errorType={}",
                exception.getClass().getSimpleName());
        return ResponseEntity.status(503).header("Cache-Control", "no-store")
                .body("Email verification is temporarily unavailable. Please try again later.");
    }
}
