package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.SmsData.*;
import com.bjworld21.congress.service.SmsCampaignService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/admin/sms")
public class SmsCampaignController {
    private final SmsCampaignService service;
    public SmsCampaignController(SmsCampaignService service) { this.service = service; }
    @GetMapping("/campaigns") public Page page(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="") String keyword, @RequestParam(defaultValue="") String status) {
        return service.page(conferenceSeq, page, keyword, status);
    }
    @GetMapping("/campaigns/{seq}") public Detail detail(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) { return service.detail(conferenceSeq, seq); }
    @PostMapping("/campaigns") @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @Valid @RequestBody CampaignRequest request, HttpSession session) {
        return service.create(conferenceSeq, request, ((Number) session.getAttribute("adminSeq")).longValue());
    }
    @PutMapping("/campaigns/{seq}") public Detail update(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq, @Valid @RequestBody CampaignRequest request) { return service.update(conferenceSeq, seq, request); }
    @DeleteMapping("/campaigns/{seq}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq) { service.delete(conferenceSeq, seq); }
    @PostMapping("/campaigns/{seq}/prepare") public Job prepare(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq, @Valid @RequestBody PrepareRequest request) { return service.prepare(conferenceSeq, seq, request); }
    @GetMapping("/campaigns/{seq}/recipients") public RecipientPage recipients(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @PathVariable Long seq, @RequestParam(defaultValue="1") int page) { return service.recipients(conferenceSeq, seq, page); }
    @GetMapping("/recipient-groups") public GroupCounts groupCounts(@RequestHeader("X-Conference-Seq") Long conferenceSeq) { return service.groupCounts(conferenceSeq); }
    @GetMapping("/member-search") public List<Candidate> members(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @RequestParam(defaultValue="") String keyword) { return service.searchMembers(conferenceSeq, keyword); }
    @GetMapping("/address-books") public List<AddressBook> addressBooks() { return service.addressBooks(); }
}
