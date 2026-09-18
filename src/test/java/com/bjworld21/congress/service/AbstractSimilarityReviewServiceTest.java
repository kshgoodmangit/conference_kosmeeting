package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AbstractSimilarityProperties;
import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.dto.AbstractSimilarityReviewItemResponse;
import com.bjworld21.congress.dto.AbstractSimilarityReviewUpdateRequest;
import com.bjworld21.congress.entity.AbstractSimilarityReviewCandidate;
import com.bjworld21.congress.repository.AbstractSimilarityReviewRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AbstractSimilarityReviewServiceTest {

    @Test
    void returnsServerAggregatesAndConfiguredThresholds() {
        AbstractSimilarityReviewRepository repository = mock(AbstractSimilarityReviewRepository.class);
        when(repository.findCandidates(eq(1L), eq(80.0), eq(90.0), anyString())).thenReturn(List.of(
                candidate(10L, null, 92.0, 96.0, false),
                candidate(20L, "CLEARED", 84.0, 91.0, false),
                candidate(30L, "VIOLATION_SUSPECTED", 82.0, 93.0, true)
        ));
        AbstractSimilarityReviewService service = service(repository, true);

        var response = service.findPage(1L, 1, 20, "", "", "", null);

        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getOpenCount()).isEqualTo(1);
        assertThat(response.getConcernCount()).isEqualTo(1);
        assertThat(response.getOverallThreshold()).isEqualTo(80.0);
        assertThat(response.getSectionThreshold()).isEqualTo(90.0);
        assertThat(response.getItems())
                .extracting(AbstractSimilarityReviewItemResponse::getReviewStatus)
                .containsExactly("PENDING", "CLEARED", "VIOLATION_SUSPECTED");
        assertThat(response.getItems().get(0).getRiskLevel()).isEqualTo("CRITICAL");
    }

    @Test
    void filtersBeforeCalculatingSummaryCounts() {
        AbstractSimilarityReviewRepository repository = mock(AbstractSimilarityReviewRepository.class);
        when(repository.findCandidates(eq(1L), eq(80.0), eq(90.0), anyString())).thenReturn(List.of(
                candidate(10L, null, 92.0, 96.0, false),
                candidate(20L, "CLEARED", 84.0, 91.0, false),
                candidate(30L, "VIOLATION_SUSPECTED", 82.0, 93.0, true)
        ));

        var response = service(repository, true).findPage(1L, 1, 20, "ABS-30", "", "HIGH", true);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getOpenCount()).isZero();
        assertThat(response.getConcernCount()).isEqualTo(1);
        assertThat(response.getItems()).singleElement().extracting(AbstractSimilarityReviewItemResponse::getAbstractSeq)
                .isEqualTo(30L);
    }

    @Test
    void createsCurrentReviewAndImmutableHistoryEntry() {
        AbstractSimilarityReviewRepository repository = mock(AbstractSimilarityReviewRepository.class);
        AbstractSimilarityReviewCandidate before = candidate(10L, null, 92.0, 96.0, false);
        AbstractSimilarityReviewCandidate after = candidate(10L, "VIOLATION_SUSPECTED", 92.0, 96.0, false);
        after.setReviewSeq(99L);
        after.setReviewOpinion("중복 결과를 확인했습니다.");
        after.setRejectionRecommended(true);
        when(repository.findCandidates(eq(1L), eq(80.0), eq(90.0), anyString()))
                .thenReturn(List.of(before), List.of(after));
        when(repository.findReview(1L, 10L)).thenReturn(null);
        doAnswer(invocation -> {
            invocation.getArgument(0, com.bjworld21.congress.entity.AbstractSimilarityReview.class).setSeq(99L);
            return 1;
        }).when(repository).insertReview(any());

        var response = service(repository, true).update(
                1L,
                10L,
                new AbstractSimilarityReviewUpdateRequest(
                        "VIOLATION_SUSPECTED", "중복 결과를 확인했습니다.", true
                ),
                7L
        );

        assertThat(response.getReviewStatus()).isEqualTo("VIOLATION_SUSPECTED");
        ArgumentCaptor<com.bjworld21.congress.entity.AbstractSimilarityReview> reviewCaptor =
                ArgumentCaptor.forClass(com.bjworld21.congress.entity.AbstractSimilarityReview.class);
        verify(repository).insertReview(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getHandledByAdminSeq()).isEqualTo(7L);
        verify(repository).insertHistory(
                99L, 1L, 10L, "PENDING", "VIOLATION_SUSPECTED",
                "중복 결과를 확인했습니다.", true, 7L
        );
    }

    @Test
    void rejectsRecommendationOutsideViolationStatuses() {
        AbstractSimilarityReviewRepository repository = mock(AbstractSimilarityReviewRepository.class);
        when(repository.findCandidates(eq(1L), eq(80.0), eq(90.0), anyString()))
                .thenReturn(List.of(candidate(10L, null, 92.0, 96.0, false)));

        assertThatThrownBy(() -> service(repository, true).update(
                1L, 10L, new AbstractSimilarityReviewUpdateRequest("CLEARED", "문제없음", true), 7L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("반려 권고");
    }

    @Test
    void disabledLicenseBlocksRepositoryAccess() {
        AbstractSimilarityReviewRepository repository = mock(AbstractSimilarityReviewRepository.class);

        assertThatThrownBy(() -> service(repository, false).findPage(1L, 1, 20, "", "", "", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화");
        verifyNoInteractions(repository);
    }

    private AbstractSimilarityReviewService service(
            AbstractSimilarityReviewRepository repository,
            boolean licenseEnabled
    ) {
        LicenseProperties licenseProperties = new LicenseProperties();
        licenseProperties.setAbstractSimilarityEnabled(licenseEnabled);
        return new AbstractSimilarityReviewService(
                licenseProperties,
                new AbstractSimilarityProperties(),
                PersonalDataTestSupport.properties(),
                repository
        );
    }

    private AbstractSimilarityReviewCandidate candidate(
            Long seq,
            String status,
            double overall,
            double highest,
            boolean stale
    ) {
        return AbstractSimilarityReviewCandidate.builder()
                .abstractSeq(seq)
                .submissionNo("ABS-" + seq)
                .title("Abstract " + seq)
                .targetAbstractSeq(seq + 100)
                .targetSubmissionNo("ABS-" + (seq + 100))
                .targetTitle("Similar " + seq)
                .overallSimilarity(overall)
                .highestSimilarity(highest)
                .highestSection("results")
                .stale(stale)
                .reviewStatus(status)
                .rejectionRecommended(false)
                .build();
    }
}
