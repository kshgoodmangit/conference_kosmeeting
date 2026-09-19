package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.LicenseProperties;
import com.bjworld21.conference.dto.ConferenceSettingsSaveAllRequest;
import com.bjworld21.conference.service.ConferenceSettingsManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conference-settings")
public class ConferenceSettingsSaveAllController {
    private static final Logger log = LoggerFactory.getLogger(ConferenceSettingsSaveAllController.class);

    private com.bjworld21.conference.publicsite.PublicSiteProperties publicSiteProperties = new com.bjworld21.conference.publicsite.PublicSiteProperties();
    @org.springframework.beans.factory.annotation.Autowired
    public void setPublicSiteProperties(com.bjworld21.conference.publicsite.PublicSiteProperties properties) { this.publicSiteProperties = properties; }

    private final ConferenceSettingsManagementService managementService;
    private final LicenseProperties licenseProperties;

    public ConferenceSettingsSaveAllController(
            ConferenceSettingsManagementService managementService,
            LicenseProperties licenseProperties
    ) {
        this.managementService = managementService;
        this.licenseProperties = licenseProperties;
    }

    @PostMapping("/save-all")
    public ResponseEntity<?> create(@RequestBody ConferenceSettingsSaveAllRequest request) {
        if (!licenseProperties.isConferenceCreationEnabled() || !publicSiteProperties.isMulti()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("현재 라이선스에서는 학회를 추가할 수 없습니다.");
        }
        return save(null, request);
    }

    @PutMapping("/{seq}/save-all")
    public ResponseEntity<?> update(
            @PathVariable Long seq,
            @RequestBody ConferenceSettingsSaveAllRequest request
    ) {
        return save(seq, request);
    }

    private ResponseEntity<?> save(Long seq, ConferenceSettingsSaveAllRequest request) {
        try {
            return ResponseEntity.ok(managementService.saveAll(seq, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to save conference settings and registration fees: conferenceSeq={}", seq, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("학회 설정과 등록비 저장 중 오류가 발생했습니다.");
        }
    }
}
