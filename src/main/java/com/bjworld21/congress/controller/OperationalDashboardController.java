package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.OperationalDashboardResponse.*;
import com.bjworld21.congress.service.OperationalDashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
public class OperationalDashboardController {
    private final OperationalDashboardService service;
    public OperationalDashboardController(OperationalDashboardService service) { this.service = service; }

    @GetMapping("/registration")
    public Registration registration(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return service.registration(conferenceSeq);
    }

    @GetMapping("/abstracts")
    public Abstracts abstracts(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return service.abstracts(conferenceSeq);
    }
}
