package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.NormalizedMailEvent;
import com.bjworld21.conference.mail.MailWebhookAdapter;
import com.bjworld21.conference.mail.MailWebhookRegistry;
import com.bjworld21.conference.service.MailProviderEventService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/mail")
public class MailWebhookController {
    private final MailWebhookRegistry registry;
    private final MailProviderEventService eventService;

    public MailWebhookController(MailWebhookRegistry registry, MailProviderEventService eventService) {
        this.registry = registry;
        this.eventService = eventService;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<?> receive(
            @PathVariable String provider,
            @RequestHeader HttpHeaders headers,
            @RequestBody byte[] rawBody
    ) {
        MailWebhookAdapter adapter = registry.find(provider).orElse(null);
        if (adapter == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("등록되지 않은 메일 Webhook 공급자입니다.");
        }
        if (!adapter.verify(headers, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Webhook 서명 검증에 실패했습니다.");
        }

        List<NormalizedMailEvent> events = adapter.parse(headers, rawBody);
        int accepted = 0;
        for (NormalizedMailEvent event : events) {
            event.setProvider(adapter.providerName());
            if (eventService.recordVerifiedEvent(event)) {
                accepted++;
            }
        }
        return ResponseEntity.ok(Map.of("received", events.size(), "accepted", accepted));
    }
}
