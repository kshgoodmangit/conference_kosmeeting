package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AbstractSimilarityProperties;
import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.dto.EmbeddingHealthResponse;
import com.bjworld21.congress.entity.AbstractEmbedding;
import com.bjworld21.congress.entity.AbstractSimilarityResult;
import com.bjworld21.congress.repository.AbstractEmbeddingRepository;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AbstractSimilarityServiceTest {

    @Test
    void returnsOneForIdenticalVectors() {
        float[] vector = {0.6f, 0.8f};

        assertThat(AbstractSimilarityService.cosineSimilarity(vector, vector))
                .isCloseTo(1.0, within(0.000001));
    }

    @Test
    void returnsZeroForOrthogonalVectors() {
        assertThat(AbstractSimilarityService.cosineSimilarity(
                new float[]{1.0f, 0.0f},
                new float[]{0.0f, 1.0f}
        )).isCloseTo(0.0, within(0.000001));
    }

    @Test
    void rejectsDifferentDimensions() {
        assertThatThrownBy(() -> AbstractSimilarityService.cosineSimilarity(
                new float[]{1.0f},
                new float[]{1.0f, 0.0f}
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesAnalysisDataForSimilarityCandidates() {
        LicenseProperties licenseProperties = new LicenseProperties();
        licenseProperties.setAbstractSimilarityEnabled(true);
        AbstractSimilarityProperties properties = new AbstractSimilarityProperties();
        AbstractSubmissionRepository submissionRepository = mock(AbstractSubmissionRepository.class);
        AbstractEmbeddingRepository embeddingRepository = mock(AbstractEmbeddingRepository.class);
        AbstractEmbeddingService embeddingService = mock(AbstractEmbeddingService.class);
        AbstractSimilarityResultStorageService resultStorageService =
                mock(AbstractSimilarityResultStorageService.class);
        AbstractSubmissionResponse first = AbstractSubmissionResponse.builder()
                .seq(10L)
                .status("submitted")
                .build();
        AbstractSubmissionResponse second = AbstractSubmissionResponse.builder()
                .seq(20L)
                .status("approved")
                .build();
        List<AbstractSubmissionResponse> candidates = List.of(first, second);
        when(submissionRepository.findSimilarityCandidates(1L)).thenReturn(candidates);
        when(embeddingService.synchronizeWithSummary(eq(candidates), any())).thenReturn(
                new AbstractEmbeddingService.SynchronizationResult(
                        new EmbeddingHealthResponse("ok", "BAAI/bge-small-en-v1.5", 384),
                        2,
                        2,
                        10
                )
        );
        when(embeddingRepository.findAll()).thenReturn(List.of(
                embedding(10L, "a".repeat(64)),
                embedding(20L, "b".repeat(64))
        ));
        AbstractSimilarityService service = new AbstractSimilarityService(
                licenseProperties,
                properties,
                submissionRepository,
                embeddingRepository,
                embeddingService,
                resultStorageService,
                PersonalDataTestSupport.properties()
        );

        var response = service.generateAnalysisData(1L);

        assertThat(response.abstractCount()).isEqualTo(2);
        assertThat(response.updatedAbstractCount()).isEqualTo(2);
        assertThat(response.generatedEmbeddingCount()).isEqualTo(10);
        assertThat(response.similarityResultCount()).isEqualTo(2);
        assertThat(response.model()).isEqualTo("BAAI/bge-small-en-v1.5");
        assertThat(response.dimension()).isEqualTo(384);
        verify(submissionRepository).findSimilarityCandidates(1L);
        verify(embeddingService).synchronizeWithSummary(eq(candidates), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AbstractSimilarityResult>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(resultStorageService).replaceAll(eq(1L), resultCaptor.capture(), any());
        assertThat(resultCaptor.getValue())
                .extracting(AbstractSimilarityResult::getSourceAbstractSeq)
                .containsExactly(10L, 20L);
        assertThat(resultCaptor.getValue())
                .allSatisfy(result -> {
                    assertThat(result.getOverallSimilarity()).isEqualTo(100.0);
                    assertThat(result.getScoringVersion()).isEqualTo("v2-weights");
                    assertThat(result.getSourceContentHash()).hasSize(64);
                    assertThat(result.getTargetContentHash()).hasSize(64);
                });
    }

    @Test
    void rejectsAnalysisDataGenerationWhenLicenseIsDisabled() {
        LicenseProperties licenseProperties = new LicenseProperties();
        AbstractSubmissionRepository submissionRepository = mock(AbstractSubmissionRepository.class);
        AbstractEmbeddingRepository embeddingRepository = mock(AbstractEmbeddingRepository.class);
        AbstractEmbeddingService embeddingService = mock(AbstractEmbeddingService.class);
        AbstractSimilarityResultStorageService resultStorageService =
                mock(AbstractSimilarityResultStorageService.class);
        AbstractSimilarityService service = new AbstractSimilarityService(
                licenseProperties,
                new AbstractSimilarityProperties(),
                submissionRepository,
                embeddingRepository,
                embeddingService,
                resultStorageService,
                PersonalDataTestSupport.properties()
        );

        assertThatThrownBy(() -> service.generateAnalysisData(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        verifyNoInteractions(submissionRepository, embeddingRepository, embeddingService, resultStorageService);
    }

    @Test
    void returnsStoredSimilarityResultsWithoutCallingEmbeddingServer() {
        LicenseProperties licenseProperties = new LicenseProperties();
        licenseProperties.setAbstractSimilarityEnabled(true);
        AbstractSimilarityProperties properties = new AbstractSimilarityProperties();
        AbstractSubmissionRepository submissionRepository = mock(AbstractSubmissionRepository.class);
        AbstractEmbeddingRepository embeddingRepository = mock(AbstractEmbeddingRepository.class);
        AbstractEmbeddingService embeddingService = mock(AbstractEmbeddingService.class);
        AbstractSimilarityResultStorageService resultStorageService =
                mock(AbstractSimilarityResultStorageService.class);
        AbstractSubmissionResponse source = AbstractSubmissionResponse.builder()
                .seq(10L)
                .status("submitted")
                .title("Current title")
                .objectiveText("Current objective")
                .build();
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 9, 9, 12, 30);
        AbstractSimilarityResult storedResult = AbstractSimilarityResult.builder()
                .sourceAbstractSeq(10L)
                .targetAbstractSeq(20L)
                .targetSubmissionNo("A-0020")
                .targetTitle("Similar abstract")
                .overallSimilarity(87.5)
                .titleSimilarity(90.0)
                .highestSection("title")
                .highestSimilarity(90.0)
                .modelName("BAAI/bge-small-en-v1.5")
                .sourceContentHash("a".repeat(64))
                .analyzedAt(analyzedAt)
                .build();
        when(submissionRepository.findBySeq(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(source);
        when(submissionRepository.countSimilarityCandidates(1L)).thenReturn(2L);
        when(resultStorageService.findBySourceAbstractSeq(1L, 10L)).thenReturn(List.of(storedResult));
        AbstractSimilarityService service = new AbstractSimilarityService(
                licenseProperties,
                properties,
                submissionRepository,
                embeddingRepository,
                embeddingService,
                resultStorageService,
                PersonalDataTestSupport.properties()
        );

        var response = service.getStoredAnalysis(1L, 10L);

        assertThat(response.getComparedCount()).isEqualTo(1);
        assertThat(response.getAnalyzedAt()).isEqualTo(analyzedAt);
        assertThat(response.getStale()).isTrue();
        assertThat(response.getMatches()).singleElement().satisfies(match -> {
            assertThat(match.getAbstractSeq()).isEqualTo(20L);
            assertThat(match.getSubmissionNo()).isEqualTo("A-0020");
            assertThat(match.getOverallSimilarity()).isEqualTo(87.5);
        });
        verifyNoInteractions(embeddingRepository, embeddingService);
    }

    @Test
    void returnsStoredComparisonWithCurrentSourceAndTargetContents() {
        LicenseProperties licenseProperties = new LicenseProperties();
        licenseProperties.setAbstractSimilarityEnabled(true);
        AbstractSubmissionRepository submissionRepository = mock(AbstractSubmissionRepository.class);
        AbstractEmbeddingRepository embeddingRepository = mock(AbstractEmbeddingRepository.class);
        AbstractEmbeddingService embeddingService = mock(AbstractEmbeddingService.class);
        AbstractSimilarityResultStorageService resultStorageService =
                mock(AbstractSimilarityResultStorageService.class);
        AbstractSubmissionResponse source = AbstractSubmissionResponse.builder()
                .seq(10L)
                .submissionNo("A-0010")
                .status("submitted")
                .title("Current title")
                .objectiveText("Current objective")
                .methodsText("Current methods")
                .resultsText("Current results")
                .conclusionsText("Current conclusions")
                .build();
        AbstractSubmissionResponse target = AbstractSubmissionResponse.builder()
                .seq(20L)
                .submissionNo("A-0020")
                .status("approved")
                .title("Similar title")
                .objectiveText("Similar objective")
                .methodsText("Similar methods")
                .resultsText("Similar results")
                .conclusionsText("Similar conclusions")
                .build();
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 9, 9, 12, 30);
        AbstractSimilarityResult storedResult = AbstractSimilarityResult.builder()
                .sourceAbstractSeq(10L)
                .targetAbstractSeq(20L)
                .targetSubmissionNo("A-0020")
                .targetTitle("Similar title")
                .overallSimilarity(87.5)
                .objectiveSimilarity(91.2)
                .highestSection("objective")
                .highestSimilarity(91.2)
                .sourceContentHash("a".repeat(64))
                .targetContentHash("b".repeat(64))
                .analyzedAt(analyzedAt)
                .build();
        when(submissionRepository.findBySeq(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(source);
        when(submissionRepository.findBySeq(1L, 20L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(target);
        when(resultStorageService.findBySourceAbstractSeq(1L, 10L)).thenReturn(List.of(storedResult));
        AbstractSimilarityService service = new AbstractSimilarityService(
                licenseProperties,
                new AbstractSimilarityProperties(),
                submissionRepository,
                embeddingRepository,
                embeddingService,
                resultStorageService,
                PersonalDataTestSupport.properties()
        );

        var response = service.getStoredComparison(1L, 10L, 20L);

        assertThat(response.getSource().getSubmissionNo()).isEqualTo("A-0010");
        assertThat(response.getSource().getResultsText()).isEqualTo("Current results");
        assertThat(response.getTarget().getSubmissionNo()).isEqualTo("A-0020");
        assertThat(response.getTarget().getResultsText()).isEqualTo("Similar results");
        assertThat(response.getSimilarity().getOverallSimilarity()).isEqualTo(87.5);
        assertThat(response.getAnalyzedAt()).isEqualTo(analyzedAt);
        assertThat(response.getStale()).isTrue();
        verifyNoInteractions(embeddingRepository, embeddingService);
    }

    @Test
    void rejectsComparisonWhenTargetIsNotInStoredResults() {
        LicenseProperties licenseProperties = new LicenseProperties();
        licenseProperties.setAbstractSimilarityEnabled(true);
        AbstractSubmissionRepository submissionRepository = mock(AbstractSubmissionRepository.class);
        AbstractEmbeddingRepository embeddingRepository = mock(AbstractEmbeddingRepository.class);
        AbstractEmbeddingService embeddingService = mock(AbstractEmbeddingService.class);
        AbstractSimilarityResultStorageService resultStorageService =
                mock(AbstractSimilarityResultStorageService.class);
        when(submissionRepository.findBySeq(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(10L)
                .status("submitted")
                .build());
        when(resultStorageService.findBySourceAbstractSeq(1L, 10L)).thenReturn(List.of());
        AbstractSimilarityService service = new AbstractSimilarityService(
                licenseProperties,
                new AbstractSimilarityProperties(),
                submissionRepository,
                embeddingRepository,
                embeddingService,
                resultStorageService,
                PersonalDataTestSupport.properties()
        );

        assertThatThrownBy(() -> service.getStoredComparison(1L, 10L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("저장된 유사 초록 비교 결과를 찾을 수 없습니다.");
    }

    private static AbstractEmbedding embedding(Long abstractSeq, String contentHash) {
        float[] vector = new float[384];
        vector[0] = 1.0f;
        return AbstractEmbedding.builder()
                .abstractSeq(abstractSeq)
                .sectionType("title")
                .modelName("BAAI/bge-small-en-v1.5")
                .dimension(384)
                .embedding(EmbeddingBinaryConverter.toBytes(vector))
                .contentHash(contentHash)
                .build();
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
