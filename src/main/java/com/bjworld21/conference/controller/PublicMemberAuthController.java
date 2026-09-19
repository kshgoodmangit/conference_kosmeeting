package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.publicsite.PublicApiRequest;
import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.entity.Member;
import com.bjworld21.conference.repository.MemberRepository;
import com.bjworld21.conference.security.MemberCredentialFingerprint;
import com.bjworld21.conference.service.ConferenceSettingsService;
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
@RequestMapping("/api/public/{conferenceSeq}/members")
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
        Long conferenceSeq = PublicApiRequest.context().conferenceSeq();
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
        PublicMemberSession.signIn(session, conferenceSeq, member.getSeq(),
                MemberCredentialFingerprint.hash(member.getPassword()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public ResponseEntity<Void> session(HttpServletRequest request) {
        PublicMemberSession memberSession = PublicMemberSession.resolve(
                request.getSession(false),
                () -> PublicApiRequest.context().conferenceSeq()
        );
        return memberSession != null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/logout")
    public void logout(HttpSession session, HttpServletResponse response) throws IOException {
        PublicMemberSession.signOut(session, PublicApiRequest.context().conferenceSeq());
        response.sendRedirect(PublicApiRequest.context().pageUrl("/"));
    }
}
