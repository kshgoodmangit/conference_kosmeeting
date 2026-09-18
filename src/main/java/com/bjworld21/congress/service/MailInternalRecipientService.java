package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.MailInternalRecipientResponse;
import com.bjworld21.congress.repository.MailInternalRecipientRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MailInternalRecipientService {
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("ALL", "MEMBER", "ADMIN", "ADDRESS_BOOK");
    private final MailInternalRecipientRepository repository;
    private final PersonalDataProperties personalDataProperties;

    public MailInternalRecipientService(
            MailInternalRecipientRepository repository,
            PersonalDataProperties personalDataProperties
    ) {
        this.repository = repository;
        this.personalDataProperties = personalDataProperties;
    }

    public List<MailInternalRecipientResponse> search(Long conferenceSeq, String keyword, String category, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.length() < 2) {
            return List.of();
        }
        String normalizedCategory = category == null ? "ALL" : category.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CATEGORIES.contains(normalizedCategory)) {
            throw new IllegalArgumentException("내부회원 검색 구분이 올바르지 않습니다.");
        }
        return repository.search(
                conferenceSeq,
                normalizedKeyword,
                normalizedCategory,
                Math.min(50, Math.max(1, limit)),
                personalDataProperties.requireDbEncString()
        );
    }
}
