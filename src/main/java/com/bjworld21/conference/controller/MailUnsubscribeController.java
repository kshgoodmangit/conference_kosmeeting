package com.bjworld21.conference.controller;

import com.bjworld21.conference.service.MailSuppressionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mail/unsubscribe")
public class MailUnsubscribeController {
    private final MailSuppressionService service;

    public MailUnsubscribeController(MailSuppressionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> unsubscribe(@RequestParam String token) {
        return ResponseEntity.ok(Map.of("message", service.unsubscribeByToken(token)));
    }
}
