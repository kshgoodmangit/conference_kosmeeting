package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.MailInternalRecipientResponse;
import com.bjworld21.congress.service.MailInternalRecipientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mail/internal-recipients")
public class MailInternalRecipientController {
    private final MailInternalRecipientService service;

    public MailInternalRecipientController(MailInternalRecipientService service) {
        this.service = service;
    }

    @GetMapping
    public List<MailInternalRecipientResponse> search(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return service.search(conferenceSeq, keyword, category, limit);
    }
}
