package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.MailRecipientPreviewResponse;
import com.bjworld21.congress.dto.MailRecipientSelectionRequest;
import com.bjworld21.congress.entity.MailRecipientCandidate;
import com.bjworld21.congress.repository.MailCampaignRepository;
import com.bjworld21.congress.repository.MailRecipientGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MailRecipientSelectionService {
    public static final List<String> GROUPS = RecipientGroups.CODES;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final MailRecipientGroupRepository groupRepository;
    private final MailCampaignRepository campaignRepository;
    private final MailAddressBookService addressBookService;
    private final PersonalDataProperties personalDataProperties;

    public MailRecipientSelectionService(MailRecipientGroupRepository groupRepository,
                                         MailCampaignRepository campaignRepository,
                                         MailAddressBookService addressBookService,
                                         PersonalDataProperties personalDataProperties) {
        this.groupRepository = groupRepository;
        this.campaignRepository = campaignRepository;
        this.addressBookService = addressBookService;
        this.personalDataProperties = personalDataProperties;
    }

    public List<String> validateGroups(List<String> groups) {
        return RecipientGroups.validate(groups);
    }

    @Transactional(readOnly = true)
    public MailRecipientPreviewResponse preview(Long conferenceSeq, MailRecipientSelectionRequest request) {
        Set<String> suppressions = suppressions();
        Map<String, List<MailRecipientCandidate>> groups = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String group : GROUPS) {
            List<MailRecipientCandidate> candidates = groupRepository.findGroup(
                    conferenceSeq, group, personalDataProperties.requireDbEncString()
            );
            groups.put(group, candidates);
            counts.put(group, evaluate(candidates, suppressions).includedCount());
        }
        List<MailRecipientCandidate> candidates = individualCandidates(request);
        for (String group : validateGroups(request.getRecipientGroups())) candidates.addAll(groups.get(group));
        Selection selection = evaluate(candidates, suppressions);
        return new MailRecipientPreviewResponse(counts, selection.includedCount(), selection.duplicateCount(),
                selection.suppressionCount(), selection.invalidCount());
    }

    public Selection resolve(Long conferenceSeq, MailRecipientSelectionRequest request) {
        List<MailRecipientCandidate> candidates = individualCandidates(request);
        for (String group : validateGroups(request.getRecipientGroups())) {
            candidates.addAll(groupRepository.findGroup(conferenceSeq, group, personalDataProperties.requireDbEncString()));
        }
        return evaluate(candidates, suppressions());
    }

    private List<MailRecipientCandidate> individualCandidates(MailRecipientSelectionRequest request) {
        List<MailRecipientCandidate> candidates = new ArrayList<>();
        List<Long> books = request.getAddressBookSeqs() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.getAddressBookSeqs()));
        for (Long book : books) addressBookService.requireAddressBook(book);
        if (!books.isEmpty()) candidates.addAll(groupRepository.findAddressBooks(
                books, personalDataProperties.requireDbEncString()
        ));
        if (request.getDirectRecipients() != null) {
            request.getDirectRecipients().forEach(recipient -> candidates.add(MailRecipientCandidate.builder()
                    .email(recipient.getEmail()).fullName(recipient.getFullName())
                    .affiliation(recipient.getAffiliation()).build()));
        }
        return candidates;
    }

    private Set<String> suppressions() {
        Set<String> result = new LinkedHashSet<>();
        campaignRepository.findActiveSuppressions(personalDataProperties.requireDbEncString())
                .forEach(email -> result.add(MailAddressBookService.normalizeEmail(email)));
        return result;
    }

    private Selection evaluate(List<MailRecipientCandidate> candidates, Set<String> suppressions) {
        Map<String, MailRecipientCandidate> unique = new LinkedHashMap<>();
        int duplicateCount = 0;
        int invalidCount = 0;
        for (MailRecipientCandidate candidate : candidates) {
            String email = MailAddressBookService.normalizeEmail(candidate.getEmail());
            if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
                invalidCount++;
                continue;
            }
            candidate.setEmail(candidate.getEmail().trim());
            candidate.setNormalizedEmail(email);
            MailRecipientCandidate existing = unique.putIfAbsent(email, candidate);
            if (existing != null) {
                duplicateCount++;
                if (existing.getContactSeq() == null && candidate.getContactSeq() != null) unique.put(email, candidate);
            }
        }
        int suppressionCount = (int) unique.keySet().stream().filter(suppressions::contains).count();
        return new Selection(new ArrayList<>(unique.values()), suppressions, duplicateCount, suppressionCount, invalidCount);
    }

    public record Selection(List<MailRecipientCandidate> recipients, Set<String> suppressions,
                            int duplicateCount, int suppressionCount, int invalidCount) {
        public int includedCount() { return recipients.size() - suppressionCount; }
    }
}
