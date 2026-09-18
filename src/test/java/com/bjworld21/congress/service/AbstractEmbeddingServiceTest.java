package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AbstractSimilarityProperties;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.dto.EmbeddingHealthResponse;
import com.bjworld21.congress.dto.EmbeddingResponse;
import com.bjworld21.congress.entity.AbstractEmbedding;
import com.bjworld21.congress.repository.AbstractEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractEmbeddingServiceTest {
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private AbstractEmbeddingRepository embeddingRepository;

    private AbstractEmbeddingService service;

    @BeforeEach
    void setUp() {
        AbstractSimilarityProperties properties = new AbstractSimilarityProperties();
        service = new AbstractEmbeddingService(properties, embeddingClient, embeddingRepository);
    }

    @Test
    void createsFiveSectionEmbeddingsForAnAbstract() {
        when(embeddingClient.health()).thenReturn(
                new EmbeddingHealthResponse("ok", "BAAI/bge-small-en-v1.5", 384)
        );
        when(embeddingRepository.findAll()).thenReturn(List.of());
        when(embeddingClient.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<List<Double>> vectors = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                List<Double> vector = new ArrayList<>(384);
                for (int dimension = 0; dimension < 384; dimension++) {
                    vector.add(dimension == 0 ? 1.0 : 0.0);
                }
                vectors.add(vector);
            }
            return new EmbeddingResponse("BAAI/bge-small-en-v1.5", 384, vectors);
        });

        AbstractSubmissionResponse submission = AbstractSubmissionResponse.builder()
                .seq(10L)
                .title("Title")
                .objectiveText("Objective")
                .methodsText("Methods")
                .resultsText("Results")
                .conclusionsText("Conclusions")
                .build();

        AbstractEmbeddingService.SynchronizationResult result =
                service.synchronizeWithSummary(List.of(submission));

        ArgumentCaptor<AbstractEmbedding> captor = ArgumentCaptor.forClass(AbstractEmbedding.class);
        verify(embeddingRepository, times(5)).upsert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AbstractEmbedding::getSectionType)
                .containsExactly("title", "objective", "methods", "results", "conclusions");
        assertThat(captor.getAllValues())
                .allSatisfy(embedding -> {
                    assertThat(embedding.getAbstractSeq()).isEqualTo(10L);
                    assertThat(embedding.getDimension()).isEqualTo(384);
                    assertThat(embedding.getEmbedding()).hasSize(384 * Float.BYTES);
                    assertThat(embedding.getContentHash()).hasSize(64);
                });
        assertThat(result.targetAbstractCount()).isEqualTo(1);
        assertThat(result.updatedAbstractCount()).isEqualTo(1);
        assertThat(result.generatedEmbeddingCount()).isEqualTo(5);
    }
}
