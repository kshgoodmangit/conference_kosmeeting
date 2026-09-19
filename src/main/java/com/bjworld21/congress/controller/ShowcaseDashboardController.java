package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.dto.ShowcaseDashboardResponse;
import com.bjworld21.congress.service.ShowcaseDashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ShowcaseDashboardController {
    private final ShowcaseDashboardService service;
    private final LicenseProperties license;

    public ShowcaseDashboardController(ShowcaseDashboardService service, LicenseProperties license) {
        this.service = service;
        this.license = license;
    }

    @GetMapping("/api/admin/dashboard/board")
    public ShowcaseDashboardResponse getDashboard(@RequestHeader("X-Conference-Seq") long conferenceSeq) {
        if (!license.isEventDashboardEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (conferenceSeq <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학회를 선택해 주세요.");
        try { return service.getDashboard(conferenceSeq); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }
}
