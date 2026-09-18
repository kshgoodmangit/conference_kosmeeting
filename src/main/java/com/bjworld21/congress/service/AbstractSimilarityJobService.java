package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.AbstractSimilarityDataGenerationResponse;
import com.bjworld21.congress.dto.AbstractSimilarityWeights;
import com.bjworld21.congress.entity.AbstractSimilarityJob;
import com.bjworld21.congress.repository.AbstractSimilarityJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

@Service
public class AbstractSimilarityJobService {
    private static final Logger log = LoggerFactory.getLogger(AbstractSimilarityJobService.class);
    private static final long SSE_TIMEOUT_MILLIS = 30L * 60L * 1000L;

    private final AbstractSimilarityJobRepository jobRepository;
    private final AbstractSimilarityService similarityService;
    private final Executor taskExecutor;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public AbstractSimilarityJobService(
            AbstractSimilarityJobRepository jobRepository,
            AbstractSimilarityService similarityService,
            @Qualifier("abstractSimilarityTaskExecutor") Executor taskExecutor
    ) {
        this.jobRepository = jobRepository;
        this.similarityService = similarityService;
        this.taskExecutor = taskExecutor;
    }

    public AbstractSimilarityJob start(Long conferenceSeq, Long adminSeq) {
        return start(conferenceSeq, adminSeq, getDefaultWeights());
    }

    public AbstractSimilarityWeights getDefaultWeights() {
        return similarityService.getDefaultWeights();
    }

    public AbstractSimilarityJob start(
            Long conferenceSeq,
            Long adminSeq,
            AbstractSimilarityWeights weights
    ) {
        jobRepository.failStaleActive(conferenceSeq);
        AbstractSimilarityJob active = jobRepository.findActive(conferenceSeq);
        if (active != null) {
            return active;
        }

        AbstractSimilarityJob job = AbstractSimilarityJob.builder()
                .conferenceSeq(conferenceSeq)
                .requestedByAdminSeq(adminSeq)
                .titleWeight(weights.title())
                .objectiveWeight(weights.objective())
                .methodsWeight(weights.methods())
                .resultsWeight(weights.results())
                .conclusionsWeight(weights.conclusions())
                .build();
        try {
            jobRepository.insert(job);
        } catch (DuplicateKeyException exception) {
            AbstractSimilarityJob concurrent = jobRepository.findActive(conferenceSeq);
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }

        try {
            taskExecutor.execute(() -> run(conferenceSeq, job.getSeq(), weights));
        } catch (RuntimeException exception) {
            jobRepository.fail(conferenceSeq, job.getSeq(), "유사도 분석 작업을 시작하지 못했습니다.");
            throw exception;
        }
        return get(conferenceSeq, job.getSeq());
    }

    public AbstractSimilarityJob get(Long conferenceSeq, Long jobSeq) {
        AbstractSimilarityJob job = jobRepository.findBySeq(conferenceSeq, jobSeq);
        if (job == null) {
            throw new IllegalArgumentException("유사도 분석 작업을 찾을 수 없습니다.");
        }
        return job;
    }

    public AbstractSimilarityJob getActive(Long conferenceSeq) {
        jobRepository.failStaleActive(conferenceSeq);
        return jobRepository.findActive(conferenceSeq);
    }

    public SseEmitter subscribe(Long conferenceSeq, Long jobSeq) {
        get(conferenceSeq, jobSeq);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CopyOnWriteArrayList<SseEmitter> jobEmitters =
                emitters.computeIfAbsent(jobSeq, key -> new CopyOnWriteArrayList<>());
        jobEmitters.add(emitter);
        emitter.onCompletion(() -> removeEmitter(jobSeq, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(jobSeq, emitter);
            emitter.complete();
        });
        emitter.onError(error -> removeEmitter(jobSeq, emitter));

        AbstractSimilarityJob latest = get(conferenceSeq, jobSeq);
        send(jobSeq, emitter, latest);
        return emitter;
    }

    private void run(Long conferenceSeq, Long jobSeq, AbstractSimilarityWeights weights) {
        try {
            updateProgress(conferenceSeq, jobSeq, new AbstractSimilarityProgress(
                    "PREPARING", 1, "유사도 분석 작업을 시작하고 있습니다.", 0, 0, 0
            ));
            AbstractSimilarityDataGenerationResponse result =
                    similarityService.generateAnalysisData(
                            conferenceSeq,
                            jobSeq,
                            weights,
                            progress -> updateProgress(conferenceSeq, jobSeq, progress)
                    );
            jobRepository.complete(
                    conferenceSeq,
                    jobSeq,
                    result.abstractCount(),
                    result.updatedAbstractCount(),
                    result.generatedEmbeddingCount(),
                    result.similarityResultCount()
            );
            publish(get(conferenceSeq, jobSeq));
        } catch (Exception exception) {
            log.error("Abstract similarity job failed: jobSeq={}", jobSeq, exception);
            String message = publicErrorMessage(exception);
            jobRepository.fail(conferenceSeq, jobSeq, message);
            publish(get(conferenceSeq, jobSeq));
        }
    }

    private void updateProgress(Long conferenceSeq, Long jobSeq, AbstractSimilarityProgress progress) {
        jobRepository.updateProgress(
                conferenceSeq,
                jobSeq,
                progress.phase(),
                Math.max(0, Math.min(progress.progressPercent(), 99)),
                progress.message(),
                Math.max(progress.processedCount(), 0),
                Math.max(progress.totalCount(), 0),
                Math.max(progress.abstractCount(), 0)
        );
        publish(get(conferenceSeq, jobSeq));
    }

    private void publish(AbstractSimilarityJob job) {
        List<SseEmitter> jobEmitters = emitters.get(job.getSeq());
        if (jobEmitters == null) {
            return;
        }
        for (SseEmitter emitter : jobEmitters) {
            send(job.getSeq(), emitter, job);
        }
        if (isTerminal(job)) {
            emitters.remove(job.getSeq());
        }
    }

    private void send(Long jobSeq, SseEmitter emitter, AbstractSimilarityJob job) {
        try {
            emitter.send(SseEmitter.event()
                    .id(job.getUpdatedAt() == null ? String.valueOf(job.getSeq()) : job.getUpdatedAt().toString())
                    .name("progress")
                    .reconnectTime(2_000L)
                    .data(job));
            if (isTerminal(job)) {
                removeEmitter(jobSeq, emitter);
                emitter.complete();
            }
        } catch (IOException | IllegalStateException exception) {
            removeEmitter(jobSeq, emitter);
        }
    }

    private void removeEmitter(Long jobSeq, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> jobEmitters = emitters.get(jobSeq);
        if (jobEmitters == null) {
            return;
        }
        jobEmitters.remove(emitter);
        if (jobEmitters.isEmpty()) {
            emitters.remove(jobSeq, jobEmitters);
        }
    }

    private boolean isTerminal(AbstractSimilarityJob job) {
        return "COMPLETED".equals(job.getStatus()) || "FAILED".equals(job.getStatus());
    }

    private String publicErrorMessage(Exception exception) {
        if (exception instanceof EmbeddingServiceException
                || exception instanceof IllegalArgumentException
                || exception instanceof IllegalStateException) {
            String message = exception.getMessage();
            if (message != null && !message.isBlank()) {
                return message.substring(0, Math.min(message.length(), 500));
            }
        }
        return "유사도 분석 처리 중 오류가 발생했습니다. 서버 로그를 확인해 주세요.";
    }
}
