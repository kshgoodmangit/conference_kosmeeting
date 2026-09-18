package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.RegistrationFeeCategoryRequest;
import com.bjworld21.congress.dto.RegistrationFeeCategoryResponse;
import com.bjworld21.congress.entity.RegistrationCategory;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import com.bjworld21.congress.repository.RegistrationFeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RegistrationFeeService {
    private static final String EARLY_BIRD = "EARLY_BIRD";
    private static final String REGULAR = "REGULAR";
    private static final String USD = "USD";
    private static final String KRW = "KRW";

    private final RegistrationFeeRepository registrationFeeRepository;
    private final ConferenceSettingsRepository conferenceSettingsRepository;

    public RegistrationFeeService(
            RegistrationFeeRepository registrationFeeRepository,
            ConferenceSettingsRepository conferenceSettingsRepository
    ) {
        this.registrationFeeRepository = registrationFeeRepository;
        this.conferenceSettingsRepository = conferenceSettingsRepository;
    }

    public List<RegistrationFeeCategoryResponse> findAll() {
        return findAll(latestConferenceSeq());
    }

    public List<RegistrationFeeCategoryResponse> findAll(Long conferenceSeq) {
        return registrationFeeRepository.findAllActiveWithFees(conferenceSeq);
    }

    @Transactional
    public List<RegistrationFeeCategoryResponse> saveAll(
            Long conferenceSeq,
            List<RegistrationFeeCategoryRequest> requests,
            List<Long> deletedCategorySeqs
    ) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("등록 구분을 한 개 이상 입력해주세요.");
        }

        List<RegistrationFeeCategoryResponse> currentRows = registrationFeeRepository.findAllActiveWithFees(conferenceSeq);
        Map<Long, RegistrationFeeCategoryResponse> currentBySeq = new LinkedHashMap<>();
        for (RegistrationFeeCategoryResponse row : currentRows) {
            currentBySeq.put(row.getSeq(), row);
        }

        Set<String> requestedCodes = new HashSet<>();

        for (int index = 0; index < requests.size(); index++) {
            RegistrationFeeCategoryRequest request = requests.get(index);
            validateRequest(request, requestedCodes);

            RegistrationCategory category;
            if (request.getSeq() == null) {
                String categoryCode = normalizeCode(request.getCategoryCode());
                if (registrationFeeRepository.countByCategoryCode(categoryCode, conferenceSeq) > 0) {
                    throw new IllegalArgumentException("이미 사용된 등록 구분 코드입니다: " + categoryCode);
                }
                category = RegistrationCategory.builder()
                        .conferenceSeq(conferenceSeq)
                        .categoryCode(categoryCode)
                        .categoryName(request.getCategoryName().trim())
                        .description(normalizeNullable(request.getDescription()))
                        .sortOrder(resolveSortOrder(request.getSortOrder(), index))
                        .isUsed(normalizeIsUsed(request.getIsUsed()))
                        .build();
                registrationFeeRepository.insertCategory(category);
            } else {
                RegistrationFeeCategoryResponse existing = currentBySeq.get(request.getSeq());
                if (existing == null) {
                    throw new IllegalArgumentException("존재하지 않는 등록 구분이 포함되어 있습니다.");
                }
                category = RegistrationCategory.builder()
                        .seq(existing.getSeq())
                        .conferenceSeq(conferenceSeq)
                        .categoryCode(existing.getCategoryCode())
                        .categoryName(request.getCategoryName().trim())
                        .description(normalizeNullable(request.getDescription()))
                        .sortOrder(resolveSortOrder(request.getSortOrder(), index))
                        .isUsed(normalizeIsUsed(request.getIsUsed()))
                        .build();
                registrationFeeRepository.updateCategory(category);
            }

            saveRateIfPresent(category.getSeq(), EARLY_BIRD, USD, request.getEarlyBirdUsdFee());
            saveRateIfPresent(category.getSeq(), EARLY_BIRD, KRW, request.getEarlyBirdKrwFee());
            saveRateIfPresent(category.getSeq(), REGULAR, USD, request.getRegularUsdFee());
            saveRateIfPresent(category.getSeq(), REGULAR, KRW, request.getRegularKrwFee());
        }

        if (deletedCategorySeqs != null) {
            for (Long deletedSeq : new HashSet<>(deletedCategorySeqs)) {
                if (deletedSeq != null && currentBySeq.containsKey(deletedSeq)) {
                    registrationFeeRepository.softDeleteCategory(deletedSeq, conferenceSeq);
                }
            }
        }

        return registrationFeeRepository.findAllActiveWithFees(conferenceSeq);
    }

    private Long latestConferenceSeq() {
        ConferenceSettings latest = conferenceSettingsRepository.findLatest();
        if (latest == null) {
            throw new IllegalArgumentException("등록된 학회가 없습니다.");
        }
        return latest.getSeq();
    }

    private void validateRequest(RegistrationFeeCategoryRequest request, Set<String> requestedCodes) {
        if (request == null) {
            throw new IllegalArgumentException("등록 구분 정보가 올바르지 않습니다.");
        }
        if (request.getCategoryName() == null || request.getCategoryName().trim().isEmpty()) {
            throw new IllegalArgumentException("등록 구분명은 필수입니다.");
        }
        if (request.getCategoryName().trim().length() > 100) {
            throw new IllegalArgumentException("등록 구분명은 100자 이내로 입력해주세요.");
        }

        String categoryCode = normalizeCode(request.getCategoryCode());
        if (!requestedCodes.add(categoryCode)) {
            throw new IllegalArgumentException("등록 구분 코드가 중복되었습니다: " + categoryCode);
        }

        String isUsed = normalizeIsUsed(request.getIsUsed());
        if ("Y".equals(isUsed) && (
                request.getEarlyBirdUsdFee() == null
                        || request.getEarlyBirdKrwFee() == null
                        || request.getRegularUsdFee() == null
                        || request.getRegularKrwFee() == null
        )) {
            throw new IllegalArgumentException(request.getCategoryName().trim() + "의 USD와 KRW 등록비를 기간별로 모두 입력해주세요.");
        }
        validateAmount(request.getEarlyBirdUsdFee());
        validateAmount(request.getEarlyBirdKrwFee());
        validateAmount(request.getRegularUsdFee());
        validateAmount(request.getRegularKrwFee());
    }

    private String normalizeCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,49}")) {
            throw new IllegalArgumentException("등록 구분 코드는 영문 대문자로 시작하는 2~50자의 영문, 숫자, 밑줄만 사용할 수 있습니다.");
        }
        return normalized;
    }

    private String normalizeIsUsed(String value) {
        return "N".equalsIgnoreCase(value) ? "N" : "Y";
    }

    private String normalizeNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private int resolveSortOrder(Integer requestedSortOrder, int index) {
        return requestedSortOrder != null ? requestedSortOrder : (index + 1) * 10;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("등록비는 0 이상이어야 합니다.");
        }
        if (amount != null && amount.scale() > 2) {
            throw new IllegalArgumentException("등록비는 소수점 둘째 자리까지만 입력할 수 있습니다.");
        }
    }

    private void saveRateIfPresent(Long categorySeq, String periodType, String currency, BigDecimal amount) {
        if (amount != null) {
            registrationFeeRepository.upsertRate(categorySeq, periodType, currency, amount);
        }
    }
}
