package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.publicsite.PublicSiteProperties;
import com.bjworld21.congress.service.ConferenceCopyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/conference-settings")
public class ConferenceCopyController {
    private final ConferenceCopyService service;
    private final LicenseProperties license;
    private final PublicSiteProperties sites;

    public ConferenceCopyController(ConferenceCopyService service, LicenseProperties license, PublicSiteProperties sites) {
        this.service = service; this.license = license; this.sites = sites;
    }

    @PostMapping("/{sourceSeq}/copy")
    public ResponseEntity<?> copy(@PathVariable long sourceSeq, @RequestBody ConferenceCopyService.Request request) {
        if (!license.isConferenceCreationEnabled() || !sites.isMulti())
            return ResponseEntity.status(403).body("현재 라이선스에서는 학회를 복사할 수 없습니다.");
        try { return ResponseEntity.status(201).body(service.copy(sourceSeq, request)); }
        catch (IllegalArgumentException | IllegalStateException exception) { return ResponseEntity.badRequest().body(exception.getMessage()); }
        catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("행사 복사 실패: source={}", sourceSeq, exception);
            return ResponseEntity.internalServerError().body("학회 복사에 실패했습니다. 새 행사 생성은 취소되었습니다.");
        }
    }
}
