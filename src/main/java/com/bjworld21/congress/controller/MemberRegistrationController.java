package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.MemberRegisterRequest;
import com.bjworld21.congress.dto.MemberRegisterResponse;
import com.bjworld21.congress.service.MemberService;
import com.bjworld21.congress.service.ConferenceSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberRegistrationController {
    private final MemberService memberService;
    private final ConferenceSettingsService conferenceSettingsService;

    public MemberRegistrationController(
            MemberService memberService,
            ConferenceSettingsService conferenceSettingsService
    ) {
        this.memberService = memberService;
        this.conferenceSettingsService = conferenceSettingsService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestParam String memberType,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String institution,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String positionTitle,
            @RequestParam(required = false) String country,
            @RequestParam String mobile,
            @RequestParam(defaultValue = "false") Boolean newsletter
    ) {
        try {
            MemberRegisterRequest request = MemberRegisterRequest.builder()
                    .memberType(memberType)
                    .email(email)
                    .password(password)
                    .firstName(firstName)
                    .lastName(lastName)
                    .institution(institution)
                    .department(department)
                    .positionTitle(positionTitle)
                    .country(country)
                    .mobile(mobile)
                    .newsletter(newsletter)
                    .build();

            MemberRegisterResponse response = memberService.register(
                    conferenceSettingsService.getLatestConferenceSeq(),
                    request
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred during registration");
        }
    }
}
