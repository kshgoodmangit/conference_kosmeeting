package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.publicsite.PublicApiRequest;
import com.bjworld21.conference.dto.MemberListResponse;
import com.bjworld21.conference.dto.MemberRegisterRequest;
import com.bjworld21.conference.dto.MemberRegisterResponse;
import com.bjworld21.conference.service.ConferenceSettingsService;
import com.bjworld21.conference.service.MemberService;
import com.bjworld21.conference.service.MemberEmailVerificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.regex.Pattern;

/** Handles the two public sign-up forms and the signed-in member profile form. */
@RestController
@RequestMapping("/api/public/{conferenceSeq}/members")
@IpAccessExempt
public class PublicMemberRegistrationController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern MOBILE_COUNTRY_CODE_PATTERN = Pattern.compile("^\\+\\d{1,4}$");
    private static final Pattern MOBILE_PHONE_NUMBER_PATTERN = Pattern.compile("^[0-9\\-\\s()]+$");

    private final MemberService memberService;
    private final ConferenceSettingsService conferenceSettingsService;
    private final MemberEmailVerificationService emailVerification;

    public PublicMemberRegistrationController(
            MemberService memberService,
            ConferenceSettingsService conferenceSettingsService,
            MemberEmailVerificationService emailVerification
    ) {
        this.memberService = memberService;
        this.conferenceSettingsService = conferenceSettingsService;
        this.emailVerification = emailVerification;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestParam String memberType,
            @RequestParam(required = false) String country,
            @RequestParam String email,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String institution,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String positionTitle,
            @RequestParam String password,
            @RequestParam String passwordConfirm,
            @RequestParam String mobileCountryCode,
            @RequestParam String mobilePhoneNumber,
            @RequestParam(defaultValue = "false") Boolean newsletter,
            @RequestParam(defaultValue = "false") Boolean privacyConsent,
            jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        try {
            String normalizedEmail = email.trim();
            String normalizedPassword = password.trim();
            String normalizedPasswordConfirm = passwordConfirm.trim();
            String normalizedMemberType = memberType.trim().toLowerCase(Locale.ROOT);

            if (!Boolean.TRUE.equals(privacyConsent)) {
                throw new IllegalArgumentException("Privacy consent is required");
            }
            if (!"international".equals(normalizedMemberType) && !"domestic".equals(normalizedMemberType)) {
                throw new IllegalArgumentException("Invalid member type");
            }
            if ("international".equals(normalizedMemberType) && (country == null || country.isBlank())) {
                throw new IllegalArgumentException("Country is required for international members");
            }
            if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                throw new IllegalArgumentException("Invalid email format");
            }
            if (!normalizedPassword.equals(normalizedPasswordConfirm)) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            MemberRegisterRequest request = MemberRegisterRequest.builder()
                    .memberType(normalizedMemberType)
                    .country("international".equals(normalizedMemberType) ? country.trim() : null)
                    .email(normalizedEmail)
                    .firstName(firstName.trim())
                    .lastName(lastName.trim())
                    .institution(institution.trim())
                    .department(department)
                    .positionTitle(positionTitle)
                    .password(normalizedPassword)
                    .mobile(normalizeMobile(mobileCountryCode, mobilePhoneNumber))
                    .newsletter(Boolean.TRUE.equals(newsletter))
                    .build();

            long conferenceSeq = PublicApiRequest.context().conferenceSeq();
            MemberRegisterResponse response = emailVerification.completeRegistration(conferenceSeq, normalizedEmail,
                    servletRequest.getSession(false), () -> memberService.register(conferenceSeq, request));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred during registration");
        }
    }

    @PostMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestParam(required = false) String country,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String institution,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String positionTitle,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String passwordConfirm,
            @RequestParam String mobileCountryCode,
            @RequestParam String mobilePhoneNumber,
            @RequestParam(defaultValue = "false") Boolean newsletter,
            HttpSession session
    ) {
        PublicMemberSession member = PublicMemberSession.resolve(
                session,
                () -> PublicApiRequest.context().conferenceSeq()
        );
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login is required");
        }

        try {
            MemberListResponse current = memberService.findDetail(member.conferenceSeq(), member.memberSeq()).getMember();
            if ((password != null && !password.isBlank()) || (passwordConfirm != null && !passwordConfirm.isBlank())) {
                throw new IllegalArgumentException("Please use Change Password to update your password.");
            }
            MemberRegisterRequest request = MemberRegisterRequest.builder()
                    .memberType(current.getMemberType())
                    .country("international".equals(current.getMemberType()) && country != null ? country.trim() : null)
                    .email(current.getEmail())
                    .firstName(firstName.trim())
                    .lastName(lastName.trim())
                    .institution(institution.trim())
                    .department(department)
                    .positionTitle(positionTitle)
                    .mobile(normalizeMobile(mobileCountryCode, mobilePhoneNumber))
                    .newsletter(Boolean.TRUE.equals(newsletter))
                    .build();

            return ResponseEntity.ok(memberService.update(member.conferenceSeq(), member.memberSeq(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while updating profile");
        }
    }

    private String normalizeMobile(String mobileCountryCode, String mobilePhoneNumber) {
        String normalizedMobileCountryCode = mobileCountryCode.trim();
        String normalizedMobilePhoneNumber = mobilePhoneNumber.trim();
        if (!MOBILE_COUNTRY_CODE_PATTERN.matcher(normalizedMobileCountryCode).matches()) {
            throw new IllegalArgumentException("Invalid mobile country code");
        }
        if (!MOBILE_PHONE_NUMBER_PATTERN.matcher(normalizedMobilePhoneNumber).matches()) {
            throw new IllegalArgumentException("Invalid mobile number");
        }
        return normalizedMobileCountryCode + " " + normalizedMobilePhoneNumber;
    }
}
