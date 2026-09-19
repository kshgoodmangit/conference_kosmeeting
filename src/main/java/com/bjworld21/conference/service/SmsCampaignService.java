package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.SmsData.*;
import com.bjworld21.conference.repository.SmsCampaignRepository;
import com.bjworld21.conference.sms.SmsGatewayProperties;
import com.bjworld21.conference.sms.SmsPhoneNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SmsCampaignService {
    private final SmsCampaignRepository repository;
    private final SmsGatewayProperties properties;
    private final PersonalDataProperties personalDataProperties;
    public SmsCampaignService(
            SmsCampaignRepository repository,
            SmsGatewayProperties properties,
            PersonalDataProperties personalDataProperties
    ) {
        this.repository = repository;
        this.properties = properties;
        this.personalDataProperties = personalDataProperties;
    }
    @Transactional(readOnly = true)
    public Page page(Long conferenceSeq, int page, String keyword, String status) {
        String filter = status == null ? "" : status;
        if (!Set.of("", "DRAFT", "PREPARED").contains(filter)) throw new IllegalArgumentException("문자 상태가 올바르지 않습니다.");
        String search = keyword == null ? "" : keyword.trim();
        if (search.length() > 100) throw new IllegalArgumentException("검색어는 100자 이내로 입력해 주세요.");
        Summary summary = repository.summary(conferenceSeq, search, filter, dbEncString());
        int pages = Math.max(1, (int) Math.ceil(summary.getTotalCount() / 10.0));
        int current = Math.min(Math.max(1, page), pages);
        return new Page(repository.findPage(conferenceSeq, search, filter, 10, (current - 1) * 10, dbEncString()), current, pages, summary);
    }
    @Transactional(readOnly = true)
    public Detail detail(Long conferenceSeq, Long seq) {
        return new Detail(require(conferenceSeq, seq, false), selection(seq), repository.job(seq));
    }
    @Transactional
    public Detail create(Long conferenceSeq, CampaignRequest request, Long adminSeq) {
        Campaign campaign = campaign(request);
        campaign.setCreatedBy(adminSeq);
        repository.insert(conferenceSeq, campaign, dbEncString());
        saveSources(campaign.getSeq(), request);
        return detail(conferenceSeq, campaign.getSeq());
    }
    @Transactional
    public Detail update(Long conferenceSeq, Long seq, CampaignRequest request) {
        requireDraft(require(conferenceSeq, seq, true));
        if (request.getVersionNo() == null) throw new IllegalArgumentException("수정 버전이 없습니다. 다시 열어 주세요.");
        Campaign campaign = campaign(request);
        campaign.setSeq(seq);
        campaign.setVersionNo(request.getVersionNo());
        if (repository.update(conferenceSeq, campaign, dbEncString()) != 1) throw new IllegalStateException("다른 관리자가 수정했습니다. 다시 열어 주세요.");
        repository.deleteSources(seq);
        saveSources(seq, request);
        return detail(conferenceSeq, seq);
    }
    @Transactional
    public void delete(Long conferenceSeq, Long seq) {
        requireDraft(require(conferenceSeq, seq, true));
        if (repository.delete(conferenceSeq, seq) != 1) throw new IllegalStateException("작성 중인 문자만 삭제할 수 있습니다.");
    }
    @Transactional(readOnly = true)
    public GroupCounts groupCounts(Long conferenceSeq) {
        Set<String> blocked = suppressions();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String group : RecipientGroups.CODES) {
            Set<String> phones = new HashSet<>();
            for (Candidate candidate : repository.group(
                    conferenceSeq, group, personalDataProperties.requireDbEncString()
            )) {
                String phone = SmsPhoneNumbers.recipient(candidate.getPhoneNumber());
                if (phone != null && !blocked.contains(phone)) phones.add(phone);
            }
            counts.put(group, phones.size());
        }
        return new GroupCounts(counts);
    }
    public List<Candidate> searchMembers(Long conferenceSeq, String keyword) {
        String search = keyword == null ? "" : keyword.trim();
        if (search.length() < 2) return List.of();
        if (search.length() > 100) throw new IllegalArgumentException("검색어는 100자 이내로 입력해 주세요.");
        return repository.searchMembers(conferenceSeq, search, personalDataProperties.requireDbEncString());
    }
    public List<AddressBook> addressBooks() { return repository.addressBooks(dbEncString()); }
    @Transactional(readOnly = true)
    public RecipientPage recipients(Long conferenceSeq, Long seq, int page) {
        require(conferenceSeq, seq, false);
        long count = repository.recipientCount(seq);
        int pages = Math.max(1, (int) Math.ceil(count / 20.0));
        int current = Math.min(Math.max(1, page), pages);
        return new RecipientPage(repository.recipients(seq, 20, (current - 1) * 20, dbEncString()), current, pages, count);
    }
    @Transactional
    public Job prepare(Long conferenceSeq, Long seq, PrepareRequest request) {
        Campaign campaign = require(conferenceSeq, seq, true);
        Job existing = repository.job(seq);
        if (existing != null && existing.getIdempotencyKey().equals(request.getIdempotencyKey())) return existing;
        requireDraft(campaign);
        if (repository.jobByKey(request.getIdempotencyKey()) != null) throw new IllegalStateException("이미 사용된 발송 준비 요청입니다.");
        Selection selected = selection(seq);
        List<Candidate> candidates = new ArrayList<>();
        for (String group : RecipientGroups.validate(selected.getRecipientGroups())) {
            candidates.addAll(repository.group(conferenceSeq, group, personalDataProperties.requireDbEncString()));
        }
        for (Long book : new LinkedHashSet<>(selected.getAddressBookSeqs())) {
            requireBook(book);
            candidates.addAll(repository.contacts(book, dbEncString()));
        }
        for (DirectRecipient direct : selected.getDirectRecipients()) {
            Candidate candidate = new Candidate();
            candidate.setPhoneNumber(direct.getPhoneNumber()); candidate.setFullName(direct.getFullName());
            candidates.add(candidate);
        }
        Prepared prepared = prepareRecipients(candidates, suppressions());
        if (prepared.includedCount() == 0) throw new IllegalArgumentException("유효한 번호와 문자 수신 거부를 확인한 뒤 발송 가능한 수신자가 없습니다.");
        Job job = new Job();
        job.setCampaignSeq(seq); job.setIdempotencyKey(request.getIdempotencyKey());
        job.setProvider(properties.getProvider()); job.setStatus("PREPARED"); job.setScheduledAt(campaign.getScheduledAt());
        job.setIncludedCount(prepared.includedCount()); job.setExcludedCount(prepared.invalidCount() + prepared.suppressionCount());
        job.setInvalidCount(prepared.invalidCount()); job.setSuppressionCount(prepared.suppressionCount()); job.setDuplicateCount(prepared.duplicateCount());
        repository.insertJob(job);
        for (int offset = 0; offset < prepared.recipients().size(); offset += 500) {
            repository.insertRecipients(job.getSeq(), prepared.recipients().subList(offset, Math.min(offset + 500, prepared.recipients().size())), dbEncString());
        }
        if (repository.markPrepared(conferenceSeq, seq) != 1) throw new IllegalStateException("문자 발송 준비 상태를 저장하지 못했습니다.");
        return job;
    }
    public static Prepared prepareRecipients(List<Candidate> candidates, Set<String> blocked) {
        Set<String> unique = new HashSet<>();
        List<Recipient> recipients = new ArrayList<>();
        int duplicates = 0, invalid = 0, suppressed = 0, included = 0;
        for (Candidate candidate : candidates) {
            String phone = SmsPhoneNumbers.recipient(candidate.getPhoneNumber());
            if (phone != null && !unique.add(phone)) { duplicates++; continue; }
            Recipient recipient = new Recipient();
            recipient.setPhoneNumber(candidate.getPhoneNumber()); recipient.setNormalizedPhone(phone); recipient.setFullName(candidate.getFullName());
            if (phone == null) { invalid++; recipient.setExclusionReason("INVALID_PHONE"); }
            else if (blocked.contains(phone)) { suppressed++; recipient.setExclusionReason("SUPPRESSED"); }
            else included++;
            recipient.setStatus(recipient.getExclusionReason() == null ? "READY" : "EXCLUDED");
            recipients.add(recipient);
        }
        return new Prepared(recipients, included, duplicates, invalid, suppressed);
    }
    public record Prepared(List<Recipient> recipients, int includedCount, int duplicateCount, int invalidCount, int suppressionCount) {}
    private Set<String> suppressions() {
        Set<String> result = new HashSet<>();
        for (String value : repository.suppressedPhones(dbEncString())) {
            String phone = SmsPhoneNumbers.recipient(value);
            if (phone != null) result.add(phone);
        }
        return result;
    }
    private Selection selection(Long seq) {
        Selection selection = new Selection();
        for (Source source : repository.sources(seq, dbEncString())) {
            switch (source.getSourceType()) {
                case "GROUP" -> selection.getRecipientGroups().add(source.getGroupCode());
                case "ADDRESS_BOOK" -> selection.getAddressBookSeqs().add(source.getAddressBookSeq());
                case "DIRECT" -> {
                    DirectRecipient direct = new DirectRecipient();
                    direct.setPhoneNumber(source.getPhoneNumber()); direct.setFullName(source.getFullName());
                    selection.getDirectRecipients().add(direct);
                }
                default -> throw new IllegalStateException("저장된 수신 대상 구분을 확인해 주세요.");
            }
        }
        return selection;
    }
    private void saveSources(Long seq, Selection selection) {
        for (String group : RecipientGroups.validate(selection.getRecipientGroups())) {
            Source source = new Source(); source.setSourceType("GROUP"); source.setGroupCode(group); repository.insertSource(seq, source, dbEncString());
        }
        for (Long book : new LinkedHashSet<>(selection.getAddressBookSeqs() == null ? List.<Long>of() : selection.getAddressBookSeqs())) {
            requireBook(book);
            Source source = new Source(); source.setSourceType("ADDRESS_BOOK"); source.setAddressBookSeq(book); repository.insertSource(seq, source, dbEncString());
        }
        Set<String> phones = new HashSet<>();
        for (DirectRecipient direct : selection.getDirectRecipients() == null ? List.<DirectRecipient>of() : selection.getDirectRecipients()) {
            String phone = SmsPhoneNumbers.recipient(direct.getPhoneNumber());
            if (phone == null) throw new IllegalArgumentException("개별 수신자의 휴대폰 번호를 확인해 주세요. 해외 번호는 +국가번호를 포함해 입력해 주세요.");
            if (!phones.add(phone)) continue;
            Source source = new Source(); source.setSourceType("DIRECT"); source.setPhoneNumber(phone);
            source.setFullName(direct.getFullName() == null ? null : direct.getFullName().trim()); repository.insertSource(seq, source, dbEncString());
        }
    }
    private Campaign campaign(CampaignRequest request) {
        String sender = SmsPhoneNumbers.sender(request.getSenderNumber());
        if (sender == null) throw new IllegalArgumentException("발신번호는 8~11자리 숫자로 입력해 주세요.");
        Campaign campaign = new Campaign();
        campaign.setTitle(request.getTitle().trim()); campaign.setMessage(request.getMessage().trim());
        campaign.setSenderNumber(sender); campaign.setMessageType(request.getMessageType()); campaign.setScheduledAt(request.getScheduledAt());
        return campaign;
    }
    private Campaign require(Long conferenceSeq, Long seq, boolean lock) {
        Campaign campaign = lock ? repository.lock(conferenceSeq, seq, dbEncString()) : repository.find(conferenceSeq, seq, dbEncString());
        if (campaign == null) throw new IllegalArgumentException("문자 작성 내역을 찾을 수 없습니다.");
        return campaign;
    }
    private void requireDraft(Campaign campaign) {
        if (!"DRAFT".equals(campaign.getStatus())) throw new IllegalStateException("작성 중인 문자만 수정·삭제·발송 준비할 수 있습니다.");
    }
    private void requireBook(Long seq) {
        if (seq == null || repository.addressBookExists(seq) != 1) throw new IllegalArgumentException("선택한 주소록이 없습니다. 수신 대상을 다시 선택해 주세요.");
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
