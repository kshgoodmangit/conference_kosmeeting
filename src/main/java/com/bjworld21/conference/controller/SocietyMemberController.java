package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.SocietyMemberData.*;
import com.bjworld21.conference.service.SocietyMemberService;
import com.bjworld21.conference.service.SocietyMemberBulkImportService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/admin/society-members")
public class SocietyMemberController {
    private final SocietyMemberService service;
    private final SocietyMemberBulkImportService importer;
    public SocietyMemberController(SocietyMemberService service, SocietyMemberBulkImportService importer) {
        this.service = service; this.importer = importer;
    }
    @GetMapping
    public Page page(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
                     @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String memberType) {
        return service.page(page, size, keyword, memberType);
    }
    @PostMapping
    public ResponseEntity<Member> create(@RequestBody Member request) {
        return ResponseEntity.status(201).body(service.save(null, request));
    }
    @PutMapping("/{seq}")
    public Member update(@PathVariable Long seq, @RequestBody Member request) { return service.save(seq, request); }
    @DeleteMapping("/{seq}")
    public ResponseEntity<Void> delete(@PathVariable Long seq) { service.delete(seq); return ResponseEntity.noContent().build(); }
    @PostMapping("/import")
    public ImportResult importMembers(@RequestParam MultipartFile file) { return importer.importMembers(file); }
    @GetMapping("/fee-mappings")
    public List<FeeMapping> mappings(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return service.mappings(conferenceSeq);
    }
    @PutMapping("/fee-mappings")
    public List<FeeMapping> saveMappings(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody List<FeeMapping> request
    ) {
        return service.saveMappings(conferenceSeq, request);
    }
    @PostMapping("/fee-quote")
    public FeeQuote quote(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody QuoteRequest request
    ) {
        return service.quote(conferenceSeq, request);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) { return ResponseEntity.badRequest().body(exception.getMessage()); }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<String> duplicate() { return ResponseEntity.status(409).body("이미 등록된 면허번호입니다."); }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException exception) { return ResponseEntity.status(409).body(exception.getMessage()); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<String> notFound(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getReason());
    }
}
