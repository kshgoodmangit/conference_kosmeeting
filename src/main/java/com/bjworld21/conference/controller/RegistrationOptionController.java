package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.RegistrationOptionData.*;
import com.bjworld21.conference.service.RegistrationOptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/conference-settings/{conferenceSeq}/registration-options")
public class RegistrationOptionController {
    private final RegistrationOptionService service;
    public RegistrationOptionController(RegistrationOptionService service) { this.service = service; }

    @GetMapping
    public Page page(@PathVariable Long conferenceSeq, @RequestParam(defaultValue="1") int page,
                     @RequestParam(defaultValue="") String keyword, @RequestParam(defaultValue="") String status) {
        return service.page(conferenceSeq, page, keyword, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Option create(@PathVariable Long conferenceSeq, @RequestBody Option option) { return service.save(conferenceSeq, null, option); }

    @PutMapping("/{seq}")
    public Option update(@PathVariable Long conferenceSeq, @PathVariable Long seq, @RequestBody Option option) {
        return service.save(conferenceSeq, seq, option);
    }

    @DeleteMapping("/{seq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long conferenceSeq, @PathVariable Long seq) {
        service.delete(conferenceSeq, seq);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException error) { return ResponseEntity.badRequest().body(error.getMessage()); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> status(ResponseStatusException error) { return ResponseEntity.status(error.getStatusCode()).body(error.getReason()); }
}
