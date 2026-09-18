package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.IpAccessExempt;
import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.entity.Member;
import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.security.MemberCredentialFingerprint;
import com.bjworld21.congress.service.ConferenceSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.io.IOException;

/** Member login/session endpoints used by the public login form and shared header. */
@IpAccessExempt
@RestController
@RequestMapping("/api/public/members")
public class PublicMemberAuthController {
    private static final String LOGIN_FAILED = "Invalid email or password";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonalDataProperties personalDataProperties;
    private final ConferenceSettingsService conferenceSettingsService;

    public PublicMemberAuthController(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            PersonalDataProperties personalDataProperties,
            ConferenceSettingsService conferenceSettingsService
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.personalDataProperties = personalDataProperties;
        this.conferenceSettingsService = conferenceSettingsService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestParam String email,
            @RequestParam String password,
            HttpServletRequest request,
            HttpSession session
    ) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedPassword = password.trim();
        Long conferenceSeq = conferenceSettingsService.getLatestConferenceSeq();
        Member member = normalizedEmail.isEmpty() || normalizedPassword.isEmpty()
                ? null
                : memberRepository.findByEmail(
                        conferenceSeq,
                        normalizedEmail,
                        personalDataProperties.requireDbEncString()
                );

        if (member == null || member.getPassword() == null
                || !passwordEncoder.matches(normalizedPassword, member.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(LOGIN_FAILED);
        }

        if (request.isRequestedSessionIdValid()) {
            request.changeSessionId();
        }
        // Public controllers use these two session attributes as the member ownership boundary.
        session.setAttribute("memberSeq", member.getSeq());
        session.setAttribute("memberConferenceSeq", conferenceSeq);
        session.setAttribute(MemberCredentialFingerprint.SESSION_ATTRIBUTE,
                MemberCredentialFingerprint.hash(member.getPassword()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public ResponseEntity<Void> session(HttpServletRequest request) {
        PublicMemberSession memberSession = PublicMemberSession.resolve(
                request.getSession(false),
                () -> conferenceSettingsService.getLatestConferenceSeq()
        );
        return memberSession != null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/logout")
    public void logout(HttpSession session, HttpServletResponse response) throws IOException {
        session.invalidate();
        response.sendRedirect("/");
    }
}
