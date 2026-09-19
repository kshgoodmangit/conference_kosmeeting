package com.bjworld21.conference.service;

import com.bjworld21.conference.config.AbstractTitleSimilarityProperties;
import com.bjworld21.conference.entity.AbstractTitleSimilarityCandidate;
import com.bjworld21.conference.repository.AbstractTitleSimilarityRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractTitleSimilarityServiceTest {

    @Test
    void normalizationTreatsCaseWhitespaceAndPunctuationAsExactTitle() {
        AbstractTitleSimilarityService service = service(mock(AbstractTitleSimilarityRepository.class));

        AbstractTitleSimilarityService.TitleScore score = service.calculate(
                "Effects of AI on Medical Education",
                " effects-of-ai ON medical education! "
        );

        assertThat(score.exactMatch()).isTrue();
        assertThat(score.similarityScore()).isEqualTo(100.0);
    }

    @Test
    void unrelatedTitlesHaveLowSimilarity() {
        AbstractTitleSimilarityService service = service(mock(AbstractTitleSimilarityRepository.class));

        AbstractTitleSimilarityService.TitleScore score = service.calculate(
                "Artificial Intelligence in Medical Education",
                "Long-term survival after lung transplantation"
        );

        assertThat(score.exactMatch()).isFalse();
        assertThat(score.similarityScore()).isLessThan(40.0);
    }

    @Test
    void submittedSaveStoresOnlyMatchesAboveThreshold() {
        AbstractTitleSimilarityRepository repository = mock(AbstractTitleSimilarityRepository.class);
        when(repository.findSource(1L, 10L)).thenReturn(candidate(10L, "Current Title", "submitted"));
        when(repository.findCandidates(1L, 10L)).thenReturn(List.of(
                candidate(11L, "current-title!", "submitted"),
                candidate(12L, "Completely Different Research", "submitted")
        ));
        when(repository.findCheckSeq(1L, 10L)).thenReturn(99L);
        AbstractTitleSimilarityService service = service(repository);

        service.refreshAfterSave(1L, 10L);

        verify(repository).upsertCheck(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(100.0),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(AbstractTitleSimilarityService.ALGORITHM_VERSION),
                any()
        );
        verify(repository).insertResult(any());
    }

    private AbstractTitleSimilarityService service(AbstractTitleSimilarityRepository repository) {
        AbstractTitleSimilarityProperties properties = new AbstractTitleSimilarityProperties();
        properties.setWarningThreshold(85.0);
        properties.setMaxMatches(5);
        return new AbstractTitleSimilarityService(properties, repository);
    }

    private AbstractTitleSimilarityCandidate candidate(Long seq, String title, String status) {
        return AbstractTitleSimilarityCandidate.builder()
                .seq(seq)
                .submissionNo("ABS-" + seq)
                .title(title)
                .status(status)
                .build();
    }
}
