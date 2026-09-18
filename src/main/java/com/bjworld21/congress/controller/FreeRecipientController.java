package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.FreeRecipientData.*;
import com.bjworld21.congress.service.FreeRecipientService;
import com.bjworld21.congress.service.FreeRecipientBulkImportService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/free-recipients")
public class FreeRecipientController {
    private final FreeRecipientService service;
    private final FreeRecipientBulkImportService importer;
    public FreeRecipientController(FreeRecipientService service, FreeRecipientBulkImportService importer) {
        this.service = service; this.importer = importer;
    }
    @GetMapping
    public Page page(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                     @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
                     @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String recipientType,
                     @RequestParam(defaultValue = "") String isUsed) { return service.page(conferenceSeq, page, size, keyword, recipientType, isUsed); }
    @GetMapping("/{seq}")
    public Recipient get(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) { return service.get(conferenceSeq, seq); }
    @PostMapping
    public ResponseEntity<Recipient> create(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @RequestBody Recipient request) { return ResponseEntity.status(201).body(service.save(conferenceSeq, null, request)); }
    @PutMapping("/{seq}")
    public Recipient update(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq, @RequestBody Recipient request) { return service.save(conferenceSeq, seq, request); }
    @DeleteMapping("/{seq}")
    public ResponseEntity<Void> delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) { service.delete(conferenceSeq, seq); return ResponseEntity.noContent().build(); }
    @PostMapping("/import")
    public ImportResult importRecipients(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @RequestParam MultipartFile file) { return importer.importRecipients(conferenceSeq, file); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) { return ResponseEntity.badRequest().body(exception.getMessage()); }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<String> duplicate() { return ResponseEntity.status(409).body("동일한 이름과 연락처의 무료 대상자가 이미 등록되어 있습니다."); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> notFound(ResponseStatusException exception) { return ResponseEntity.status(exception.getStatusCode()).body(exception.getReason()); }
}
