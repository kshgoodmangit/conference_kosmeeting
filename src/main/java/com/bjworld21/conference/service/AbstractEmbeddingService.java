package com.bjworld21.conference.service;

import com.bjworld21.conference.config.AbstractSimilarityProperties;
import com.bjworld21.conference.dto.AbstractSubmissionResponse;
import com.bjworld21.conference.dto.EmbeddingHealthResponse;
import com.bjworld21.conference.dto.EmbeddingResponse;
import com.bjworld21.conference.entity.AbstractEmbedding;
import com.bjworld21.conference.repository.AbstractEmbeddingRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class AbstractEmbeddingService {
    private static final int EMBEDDING_BATCH_SIZE = 32;

    private final AbstractSimilarityProperties properties;
    private final EmbeddingClient embeddingClient;
    private final AbstractEmbeddingRepository embeddingRepository;

    public AbstractEmbeddingService(
            AbstractSimilarityProperties properties,
            EmbeddingClient embeddingClient,
            AbstractEmbeddingRepository embeddingRepository
    ) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.embeddingRepository = embeddingRepository;
    }

    public EmbeddingHealthResponse synchronize(List<AbstractSubmissionResponse> submissions) {
        return synchronizeWithSummary(submissions).server();
    }

    public SynchronizationResult synchronizeWithSummary(List<AbstractSubmissionResponse> submissions) {
        return synchronizeWithSummary(submissions, progress -> {
        });
    }

    public SynchronizationResult synchronizeWithSummary(
            List<AbstractSubmissionResponse> submissions,
            Consumer<EmbeddingProgress> progressConsumer
    ) {
        EmbeddingHealthResponse server = embeddingClient.health();
        Map<EmbeddingKey, AbstractEmbedding> existing = new HashMap<>();
        for (AbstractEmbedding embedding : embeddingRepository.findAll()) {
            existing.put(new EmbeddingKey(embedding.getAbstractSeq(), embedding.getSectionType()), embedding);
        }

        Map<Long, AbstractSubmissionResponse> uniqueSubmissions = new LinkedHashMap<>();
        for (AbstractSubmissionResponse submission : submissions) {
            if (submission != null && submission.getSeq() != null) {
                uniqueSubmissions.put(submission.getSeq(), submission);
            }
        }

        List<PendingEmbedding> pending = new ArrayList<>();
        for (AbstractSubmissionResponse submission : uniqueSubmissions.values()) {
            for (SectionText section : sections(submission)) {
                EmbeddingKey key = new EmbeddingKey(submission.getSeq(), section.type());
                AbstractEmbedding stored = existing.get(key);
                String text = normalizeText(section.text());
                if (text.isBlank()) {
                    if (stored != null) {
                        embeddingRepository.deleteSection(submission.getSeq(), section.type());
                    }
                    continue;
                }

                String contentHash = sha256(text);
                if (isCurrent(stored, server.model(), contentHash)) {
                    continue;
                }
                pending.add(new PendingEmbedding(submission.getSeq(), section.type(), text, contentHash));
            }
        }

        for (int start = 0; start < pending.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, pending.size());
            List<PendingEmbedding> batch = pending.subList(start, end);
            EmbeddingResponse response = embeddingClient.embed(batch.stream().map(PendingEmbedding::text).toList());
            if (!server.model().equals(response.model())) {
                throw new EmbeddingServiceException("상태 확인과 벡터 변환 응답의 모델명이 다릅니다.");
            }

            for (int index = 0; index < batch.size(); index++) {
                PendingEmbedding item = batch.get(index);
                float[] vector = toFloatArray(response.embeddings().get(index));
                embeddingRepository.upsert(AbstractEmbedding.builder()
                        .abstractSeq(item.abstractSeq())
                        .sectionType(item.sectionType())
                        .modelName(response.model())
                        .dimension(response.dimension())
                        .embedding(EmbeddingBinaryConverter.toBytes(vector))
                        .contentHash(item.contentHash())
                        .build());
            }
            progressConsumer.accept(new EmbeddingProgress(end, pending.size()));
        }
        if (pending.isEmpty()) {
            progressConsumer.accept(new EmbeddingProgress(0, 0));
        }

        int updatedAbstractCount = (int) pending.stream()
                .map(PendingEmbedding::abstractSeq)
                .distinct()
                .count();
        return new SynchronizationResult(
                server,
                uniqueSubmissions.size(),
                updatedAbstractCount,
                pending.size()
        );
    }

    private boolean isCurrent(AbstractEmbedding stored, String modelName, String contentHash) {
        return stored != null
                && modelName.equals(stored.getModelName())
                && stored.getDimension() != null
                && stored.getDimension() == properties.getDimension()
                && contentHash.equals(stored.getContentHash())
                && stored.getEmbedding() != null
                && stored.getEmbedding().length == properties.getDimension() * Float.BYTES;
    }

    private float[] toFloatArray(List<Double> values) {
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }

    private List<SectionText> sections(AbstractSubmissionResponse submission) {
        return List.of(
                new SectionText("title", submission.getTitle()),
                new SectionText("objective", submission.getObjectiveText()),
                new SectionText("methods", submission.getMethodsText()),
                new SectionText("results", submission.getResultsText()),
                new SectionText("conclusions", submission.getConclusionsText())
        );
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    private record EmbeddingKey(Long abstractSeq, String sectionType) {
    }

    private record SectionText(String type, String text) {
    }

    private record PendingEmbedding(Long abstractSeq, String sectionType, String text, String contentHash) {
    }

    public record SynchronizationResult(
            EmbeddingHealthResponse server,
            int targetAbstractCount,
            int updatedAbstractCount,
            int generatedEmbeddingCount
    ) {
    }

    public record EmbeddingProgress(int processedCount, int totalCount) {
    }
}
