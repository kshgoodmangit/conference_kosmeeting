package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AbstractSimilarityProperties;
import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AbstractSimilarityReviewHistoryResponse;
import com.bjworld21.congress.dto.AbstractSimilarityReviewItemResponse;
import com.bjworld21.congress.dto.AbstractSimilarityReviewPageResponse;
import com.bjworld21.congress.dto.AbstractSimilarityReviewUpdateRequest;
import com.bjworld21.congress.entity.AbstractSimilarityReview;
import com.bjworld21.congress.entity.AbstractSimilarityReviewCandidate;
import com.bjworld21.congress.repository.AbstractSimilarityReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AbstractSimilarityReviewService {
    public static final String PENDING = "PENDING";
    public static final String IN_REVIEW = "IN_REVIEW";
    public static final String EXPLANATION_REQUESTED = "EXPLANATION_REQUESTED";
    public static final String EXPLANATION_RECEIVED = "EXPLANATION_RECEIVED";
    public static final String CLEARED = "CLEARED";
    public static final String VIOLATION_SUSPECTED = "VIOLATION_SUSPECTED";
    public static final String VIOLATION_CONFIRMED = "VIOLATION_CONFIRMED";

    private static final Set<String> STATUSES = Set.of(
            PENDING,
            IN_REVIEW,
            EXPLANATION_REQUESTED,
            EXPLANATION_RECEIVED,
            CLEARED,
            VIOLATION_SUSPECTED,
            VIOLATION_CONFIRMED
    );
    private static final Set<String> OPEN_STATUSES = Set.of(
            PENDING,
            IN_REVIEW,
            EXPLANATION_REQUESTED,
            EXPLANATION_RECEIVED
    );
    private static final Set<String> CONCERN_STATUSES = Set.of(
            VIOLATION_SUSPECTED,
            VIOLATION_CONFIRMED
    );

    private final LicenseProperties licenseProperties;
    private final AbstractSimilarityProperties similarityProperties;
    private final PersonalDataProperties personalDataProperties;
    private final AbstractSimilarityReviewRepository repository;

    public AbstractSimilarityReviewService(
            LicenseProperties licenseProperties,
            AbstractSimilarityProperties similarityProperties,
            PersonalDataProperties personalDataProperties,
            AbstractSimilarityReviewRepository repository
    ) {
        this.licenseProperties = licenseProperties;
        this.similarityProperties = similarityProperties;
        this.personalDataProperties = personalDataProperties;
        this.repository = repository;
    }

    public AbstractSimilarityReviewPageResponse findPage(
            Long conferenceSeq,
            Integer page,
            Integer size,
            String keyword,
            String status,
            String riskLevel,
            Boolean stale
    ) {
        requireEnabled();
        requireConferenceSeq(conferenceSeq);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        int requestedPage = Math.max(page == null ? 1 : page, 1);
        String normalizedKeyword = normalize(keyword).toLowerCase(Locale.ROOT);
        String normalizedStatus = normalize(status).toUpperCase(Locale.ROOT);
        if (!normalizedStatus.isEmpty() && !STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("검토 상태 검색 조건이 올바르지 않습니다.");
        }
        String normalizedRiskLevel = normalize(riskLevel).toUpperCase(Locale.ROOT);
        if (!normalizedRiskLevel.isEmpty() && !Set.of("HIGH", "CRITICAL").contains(normalizedRiskLevel)) {
            throw new IllegalArgumentException("유사도 구간 검색 조건이 올바르지 않습니다.");
        }

        AbstractSimilarityProperties.ReviewTarget thresholds = validatedThresholds();
        List<AbstractSimilarityReviewItemResponse> filtered = repository.findCandidates(
                        conferenceSeq,
                        thresholds.getOverallThreshold(),
                        thresholds.getSectionThreshold(),
                        personalDataProperties.requireDbEncString()
                ).stream()
                .map(candidate -> AbstractSimilarityReviewItemResponse.from(candidate, riskLevel(candidate, thresholds)))
                .filter(item -> normalizedKeyword.isEmpty() || matchesKeyword(item, normalizedKeyword))
                .filter(item -> normalizedStatus.isEmpty() || normalizedStatus.equals(item.getReviewStatus()))
                .filter(item -> normalizedRiskLevel.isEmpty() || normalizedRiskLevel.equals(item.getRiskLevel()))
                .filter(item -> stale == null || stale.equals(item.getStale()))
                .toList();

        long totalCount = filtered.size();
        long openCount = filtered.stream().filter(item -> OPEN_STATUSES.contains(item.getReviewStatus())).count();
        long concernCount = filtered.stream().filter(item ->
                CONCERN_STATUSES.contains(item.getReviewStatus()) || Boolean.TRUE.equals(item.getRejectionRecommended())
        ).count();
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) normalizedSize));
        int normalizedPage = Math.min(requestedPage, totalPages);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, filtered.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());

        return AbstractSimilarityReviewPageResponse.builder()
                .items(filtered.subList(fromIndex, toIndex))
                .page(normalizedPage)
                .size(normalizedSize)
                .totalPages(totalPages)
                .totalCount(totalCount)
                .openCount(openCount)
                .concernCount(concernCount)
                .overallThreshold(thresholds.getOverallThreshold())
                .sectionThreshold(thresholds.getSectionThreshold())
                .criticalOverallThreshold(thresholds.getCriticalOverallThreshold())
                .criticalSectionThreshold(thresholds.getCriticalSectionThreshold())
                .build();
    }

    public List<AbstractSimilarityReviewHistoryResponse> findHistory(Long conferenceSeq, Long abstractSeq) {
        requireEnabled();
        requireCandidate(conferenceSeq, abstractSeq);
        return repository.findHistory(
                conferenceSeq,
                abstractSeq,
                personalDataProperties.requireDbEncString()
        );
    }

    @Transactional
    public AbstractSimilarityReviewItemResponse update(
            Long conferenceSeq,
            Long abstractSeq,
            AbstractSimilarityReviewUpdateRequest request,
            Long adminSeq
    ) {
        requireEnabled();
        requireConferenceSeq(conferenceSeq);
        if (adminSeq == null || adminSeq <= 0) {
            throw new IllegalArgumentException("처리자 정보를 확인할 수 없습니다.");
        }
        AbstractSimilarityReviewCandidate candidate = requireCandidate(conferenceSeq, abstractSeq);
        if (request == null) {
            throw new IllegalArgumentException("검토 내용을 입력해 주세요.");
        }

        String status = normalize(request.getStatus()).toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("검토 상태가 올바르지 않습니다.");
        }
        String opinion = normalizeNullable(request.getReviewOpinion());
        if (opinion != null && opinion.length() > 4000) {
            throw new IllegalArgumentException("관리자 검토 의견은 4,000자 이하로 입력해 주세요.");
        }
        if (Set.of(EXPLANATION_REQUESTED, VIOLATION_SUSPECTED, VIOLATION_CONFIRMED).contains(status)
                && opinion == null) {
            throw new IllegalArgumentException("선택한 검토 상태에는 관리자 검토 의견이 필요합니다.");
        }
        boolean rejectionRecommended = Boolean.TRUE.equals(request.getRejectionRecommended());
        if (rejectionRecommended && !Set.of(VIOLATION_SUSPECTED, VIOLATION_CONFIRMED).contains(status)) {
            throw new IllegalArgumentException("반려 권고는 위반 의심 또는 위반 확인 상태에서만 지정할 수 있습니다.");
        }

        AbstractSimilarityReview existing = repository.findReview(conferenceSeq, abstractSeq);
        String previousStatus = existing == null ? PENDING : existing.getStatus();
        AbstractSimilarityReview review = AbstractSimilarityReview.builder()
                .seq(existing == null ? null : existing.getSeq())
                .conferenceSeq(conferenceSeq)
                .abstractSeq(abstractSeq)
                .status(status)
                .reviewOpinion(opinion)
                .rejectionRecommended(rejectionRecommended)
                .handledByAdminSeq(adminSeq)
                .build();

        if (existing == null) {
            repository.insertReview(review);
        } else if (repository.updateReview(review) != 1) {
            throw new IllegalStateException("유사도 검토 내용을 저장하지 못했습니다.");
        }
        repository.insertHistory(
                review.getSeq(), conferenceSeq, abstractSeq, previousStatus, status,
                opinion, rejectionRecommended, adminSeq
        );

        AbstractSimilarityReviewCandidate saved = requireCandidate(conferenceSeq, abstractSeq);
        return AbstractSimilarityReviewItemResponse.from(saved, riskLevel(saved, validatedThresholds()));
    }

    private AbstractSimilarityReviewCandidate requireCandidate(Long conferenceSeq, Long abstractSeq) {
        requireConferenceSeq(conferenceSeq);
        if (abstractSeq == null || abstractSeq <= 0) {
            throw new IllegalArgumentException("초록 정보를 확인할 수 없습니다.");
        }
        AbstractSimilarityProperties.ReviewTarget thresholds = validatedThresholds();
        return repository.findCandidates(
                        conferenceSeq,
                        thresholds.getOverallThreshold(),
                        thresholds.getSectionThreshold(),
                        personalDataProperties.requireDbEncString()
                ).stream()
                .filter(item -> abstractSeq.equals(item.getAbstractSeq()))
                .findFirst()
                .orElseThrow(() -> new SimilarityReviewNotFoundException(
                        "현재 검토 대상 기준에 해당하는 유사도 분석 결과를 찾을 수 없습니다."
                ));
    }

    private String riskLevel(
            AbstractSimilarityReviewCandidate candidate,
            AbstractSimilarityProperties.ReviewTarget thresholds
    ) {
        return candidate.getOverallSimilarity() >= thresholds.getCriticalOverallThreshold()
                || candidate.getHighestSimilarity() >= thresholds.getCriticalSectionThreshold()
                ? "CRITICAL"
                : "HIGH";
    }

    private boolean matchesKeyword(AbstractSimilarityReviewItemResponse item, String keyword) {
        return contains(item.getSubmissionNo(), keyword)
                || contains(item.getTitle(), keyword)
                || contains(item.getTargetSubmissionNo(), keyword)
                || contains(item.getTargetTitle(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private AbstractSimilarityProperties.ReviewTarget validatedThresholds() {
        AbstractSimilarityProperties.ReviewTarget thresholds = similarityProperties.getReviewTarget();
        validateThreshold(thresholds.getOverallThreshold());
        validateThreshold(thresholds.getSectionThreshold());
        validateThreshold(thresholds.getCriticalOverallThreshold());
        validateThreshold(thresholds.getCriticalSectionThreshold());
        return thresholds;
    }

    private void validateThreshold(double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 100.0) {
            throw new IllegalStateException("유사도 검토 대상 기준 설정이 올바르지 않습니다.");
        }
    }

    private void requireEnabled() {
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            throw new IllegalStateException("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }
    }

    private void requireConferenceSeq(Long conferenceSeq) {
        if (conferenceSeq == null || conferenceSeq <= 0) {
            throw new IllegalArgumentException("학회 정보를 확인할 수 없습니다.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    public static class SimilarityReviewNotFoundException extends IllegalArgumentException {
        public SimilarityReviewNotFoundException(String message) {
            super(message);
        }
    }
}
