package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.service.ConferenceSettingsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ConferenceSettingsController {
    private final ConferenceSettingsService conferenceSettingsService;
    private final LicenseProperties licenseProperties;

    public ConferenceSettingsController(ConferenceSettingsService conferenceSettingsService, LicenseProperties licenseProperties) {
        this.conferenceSettingsService = conferenceSettingsService;
        this.licenseProperties = licenseProperties;
    }

    public record Capabilities(
            boolean conferenceCreationEnabled,
            boolean conferenceCopyEnabled,
            boolean abstractSimilarityEnabled,
            boolean eventDashboardEnabled,
            boolean userAnalyticsDashboardEnabled
    ) {}

    @GetMapping("/admin/conference-settings/capabilities")
    public Capabilities getCapabilities() {
        return new Capabilities(
                licenseProperties.isConferenceCreationEnabled(),
                licenseProperties.isConferenceCreationEnabled(),
                licenseProperties.isAbstractSimilarityEnabled(),
                licenseProperties.isEventDashboardEnabled(),
                licenseProperties.isUserAnalyticsDashboardEnabled()
        );
    }

    @GetMapping("/conference-settings")
    public ResponseEntity<ConferenceSettingsResponse> getSettings() {
        return ResponseEntity.ok(conferenceSettingsService.getSettings());
    }

    @GetMapping("/admin/conference-settings")
    public ResponseEntity<List<ConferenceSettingsResponse>> getSettingsList() {
        return ResponseEntity.ok(conferenceSettingsService.getSettingsList());
    }

    @PostMapping("/admin/conference-settings")
    public ResponseEntity<?> saveSettings(
            @RequestParam String eventName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate earlyBirdStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate earlyBirdEndDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate regularStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate regularEndDate,
            @RequestParam(defaultValue = "USD") String registrationCurrency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abstractStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abstractEndDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate presentationMaterialStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate presentationMaterialEndDate,
            @RequestParam(required = false) String venueAddress
    ) {
        if (!licenseProperties.isConferenceCreationEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("현재 라이선스에서는 학회를 추가할 수 없습니다.");
        }
        try {
            return ResponseEntity.ok(conferenceSettingsService.saveSettings(
                    eventName,
                    eventStartDate,
                    eventEndDate,
                    earlyBirdStartDate,
                    earlyBirdEndDate,
                    regularStartDate,
                    regularEndDate,
                    registrationCurrency,
                    abstractStartDate,
                    abstractEndDate,
                    presentationMaterialStartDate,
                    presentationMaterialEndDate,
                    venueAddress
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("행사 설정 저장 중 오류가 발생했습니다.");
        }
    }

    @PutMapping("/admin/conference-settings/{seq}")
    public ResponseEntity<?> updateSettings(
            @PathVariable Long seq,
            @RequestParam String eventName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate earlyBirdStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate earlyBirdEndDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate regularStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate regularEndDate,
            @RequestParam(defaultValue = "USD") String registrationCurrency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abstractStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abstractEndDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate presentationMaterialStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate presentationMaterialEndDate,
            @RequestParam(required = false) String venueAddress
    ) {
        try {
            return ResponseEntity.ok(conferenceSettingsService.updateSettings(
                    seq,
                    eventName,
                    eventStartDate,
                    eventEndDate,
                    earlyBirdStartDate,
                    earlyBirdEndDate,
                    regularStartDate,
                    regularEndDate,
                    registrationCurrency,
                    abstractStartDate,
                    abstractEndDate,
                    presentationMaterialStartDate,
                    presentationMaterialEndDate,
                    venueAddress
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("학회 설정 수정 중 오류가 발생했습니다.");
        }
    }
}
