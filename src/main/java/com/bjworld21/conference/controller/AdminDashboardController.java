package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AdminDashboardResponse;
import com.bjworld21.conference.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public ResponseEntity<AdminDashboardResponse> getDashboard(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        return ResponseEntity.ok(adminDashboardService.getDashboard(conferenceSeq));
    }
}
