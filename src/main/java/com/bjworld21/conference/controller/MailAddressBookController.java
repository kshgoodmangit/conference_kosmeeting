package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.MailAddressBookRequest;
import com.bjworld21.conference.dto.MailContactRequest;
import com.bjworld21.conference.dto.MailAddressBookImportResponse;
import com.bjworld21.conference.entity.MailAddressBook;
import com.bjworld21.conference.entity.MailContact;
import com.bjworld21.conference.service.MailAddressBookService;
import com.bjworld21.conference.service.MailAddressBookImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mail/address-books")
public class MailAddressBookController {
    private final MailAddressBookService service;
    private final MailAddressBookImportService importService;

    public MailAddressBookController(MailAddressBookService service, MailAddressBookImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    @GetMapping
    public List<MailAddressBook> list(@RequestParam(defaultValue = "") String keyword) {
        return service.findAll(keyword);
    }

    @PostMapping
    public ResponseEntity<MailAddressBook> create(@Valid @RequestBody MailAddressBookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{seq}")
    public MailAddressBook update(@PathVariable Long seq, @Valid @RequestBody MailAddressBookRequest request) {
        return service.update(seq, request);
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<Void> delete(@PathVariable Long seq) {
        service.delete(seq);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{seq}/contacts")
    public List<MailContact> contacts(@PathVariable Long seq, @RequestParam(defaultValue = "") String keyword) {
        return service.findContacts(seq, keyword);
    }

    @PostMapping("/{seq}/contacts")
    public ResponseEntity<MailContact> saveContact(
            @PathVariable Long seq,
            @Valid @RequestBody MailContactRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveContact(seq, request));
    }

    @PostMapping(value = "/{seq}/contacts/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MailAddressBookImportResponse> importContacts(
            @PathVariable Long seq,
            @RequestPart("file") MultipartFile file
    ) {
        MailAddressBookImportResponse result = importService.importContacts(seq, file);
        return result.errorCount() > 0 ? ResponseEntity.badRequest().body(result) : ResponseEntity.ok(result);
    }

    @DeleteMapping("/{seq}/contacts/{contactSeq}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long seq, @PathVariable Long contactSeq) {
        service.deleteContact(seq, contactSeq);
        return ResponseEntity.noContent().build();
    }
}
