package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.AbstractSimilarityDataGenerationResponse;
import com.bjworld21.conference.dto.AbstractSimilarityWeights;
import com.bjworld21.conference.entity.AbstractSimilarityJob;
import com.bjworld21.conference.repository.AbstractSimilarityJobRepository;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractSimilarityJobServiceTest {

    @Test
    void startsAnalysisAndCompletesJob() {
        AbstractSimilarityJobRepository jobRepository = mock(AbstractSimilarityJobRepository.class);
        AbstractSimilarityService similarityService = mock(AbstractSimilarityService.class);
        Executor directExecutor = Runnable::run;
        AbstractSimilarityJob storedJob = AbstractSimilarityJob.builder()
                .seq(15L)
                .status("RUNNING")
                .phase("CALCULATING")
                .progressPercent(50)
                .build();
        AbstractSimilarityWeights weights = new AbstractSimilarityWeights(10, 20, 20, 30, 20);

        when(jobRepository.findActive(1L)).thenReturn(null);
        when(similarityService.getDefaultWeights()).thenReturn(weights);
        doAnswer(invocation -> {
            AbstractSimilarityJob job = invocation.getArgument(0);
            job.setSeq(15L);
            return null;
        }).when(jobRepository).insert(any(AbstractSimilarityJob.class));
        when(jobRepository.findBySeq(eq(1L), anyLong())).thenReturn(storedJob);
        when(similarityService.generateAnalysisData(eq(1L), eq(15L), eq(weights), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<AbstractSimilarityProgress> progressConsumer = invocation.getArgument(3);
            progressConsumer.accept(new AbstractSimilarityProgress(
                    "CALCULATING", 60, "계산 중", 10, 20, 20
            ));
            return new AbstractSimilarityDataGenerationResponse(
                    "BAAI/bge-small-en-v1.5", 384, 20, 2, 10, 200
            );
        });
        AbstractSimilarityJobService service = new AbstractSimilarityJobService(
                jobRepository,
                similarityService,
                directExecutor
        );

        AbstractSimilarityJob response = service.start(1L, 7L);

        assertThat(response.getSeq()).isEqualTo(15L);
        verify(jobRepository).insert(any(AbstractSimilarityJob.class));
        verify(jobRepository, atLeastOnce()).updateProgress(
                eq(1L), anyLong(), anyString(), anyInt(), anyString(), anyInt(), anyInt(), anyInt()
        );
        verify(jobRepository).complete(1L, 15L, 20, 2, 10, 200);
    }
}
