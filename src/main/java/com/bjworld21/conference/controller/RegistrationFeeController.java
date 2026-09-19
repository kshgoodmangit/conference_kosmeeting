package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.RegistrationFeeCategoryResponse;
import com.bjworld21.conference.dto.RegistrationFeeSettingsRequest;
import com.bjworld21.conference.service.RegistrationFeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RegistrationFeeController {
    private final RegistrationFeeService registrationFeeService;

    public RegistrationFeeController(RegistrationFeeService registrationFeeService) {
        this.registrationFeeService = registrationFeeService;
    }

    @GetMapping("/public/{conferenceSeq}/registration-fees")
    @com.bjworld21.conference.config.IpAccessExempt
    public ResponseEntity<List<RegistrationFeeCategoryResponse>> findAll() {
        return ResponseEntity.ok(registrationFeeService.findAll(com.bjworld21.conference.publicsite.PublicApiRequest.context().conferenceSeq()));
    }

    @GetMapping("/admin/registration-fees")
    public ResponseEntity<List<RegistrationFeeCategoryResponse>> findAllForSelectedConference(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        return ResponseEntity.ok(registrationFeeService.findAll(conferenceSeq));
    }

    @GetMapping("/admin/conference-settings/{conferenceSeq}/registration-fees")
    public ResponseEntity<List<RegistrationFeeCategoryResponse>> findAllForConference(
            @PathVariable Long conferenceSeq
    ) {
        return ResponseEntity.ok(registrationFeeService.findAll(conferenceSeq));
    }

    @PutMapping("/admin/registration-fees")
    public ResponseEntity<?> saveAll(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody RegistrationFeeSettingsRequest request
    ) {
        try {
            return ResponseEntity.ok(registrationFeeService.saveAll(
                    conferenceSeq,
                    request.getCategories(),
                    request.getDeletedCategorySeqs()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("등록비 저장 중 오류가 발생했습니다.");
        }
    }
}
