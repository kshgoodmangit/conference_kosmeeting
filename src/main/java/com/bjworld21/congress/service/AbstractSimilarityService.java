package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AbstractSimilarityProperties;
import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AbstractSimilarityComparisonResponse;
import com.bjworld21.congress.dto.AbstractSimilarityContentResponse;
import com.bjworld21.congress.dto.AbstractSimilarityMatchResponse;
import com.bjworld21.congress.dto.AbstractSimilarityResponse;
import com.bjworld21.congress.dto.AbstractSimilarityDataGenerationResponse;
import com.bjworld21.congress.dto.AbstractSimilarityWeights;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.dto.EmbeddingHealthResponse;
import com.bjworld21.congress.entity.AbstractEmbedding;
import com.bjworld21.congress.entity.AbstractSimilarityResult;
import com.bjworld21.congress.repository.AbstractEmbeddingRepository;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class AbstractSimilarityService {
    private static final List<String> SECTIONS = List.of(
            "title", "objective", "methods", "results", "conclusions"
    );
    private static final String SCORING_VERSION = "v2-weights";

    private final LicenseProperties licenseProperties;
    private final AbstractSimilarityProperties properties;
    private final AbstractSubmissionRepository submissionRepository;
    private final AbstractEmbeddingRepository embeddingRepository;
    private final AbstractEmbeddingService embeddingService;
    private final AbstractSimilarityResultStorageService resultStorageService;
    private final PersonalDataProperties personalDataProperties;

    public AbstractSimilarityService(
            LicenseProperties licenseProperties,
            AbstractSimilarityProperties properties,
            AbstractSubmissionRepository submissionRepository,
            AbstractEmbeddingRepository embeddingRepository,
            AbstractEmbeddingService embeddingService,
            AbstractSimilarityResultStorageService resultStorageService,
            PersonalDataProperties personalDataProperties
    ) {
        this.licenseProperties = licenseProperties;
        this.properties = properties;
        this.submissionRepository = submissionRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
        this.resultStorageService = resultStorageService;
        this.personalDataProperties = personalDataProperties;
    }

    public AbstractSimilarityResponse analyze(Long conferenceSeq, Long abstractSeq) {
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            throw new IllegalStateException("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }

        AbstractSubmissionResponse target = submissionRepository.findBySeq(
                conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
        );
        if (target == null) {
            throw new IllegalArgumentException("초록 정보를 찾을 수 없습니다.");
        }

        List<AbstractSubmissionResponse> candidates = submissionRepository.findSimilarityCandidates(conferenceSeq);
        List<AbstractSubmissionResponse> synchronizationTargets = new ArrayList<>(candidates.size() + 1);
        synchronizationTargets.add(target);
        synchronizationTargets.addAll(candidates);
        EmbeddingHealthResponse server = embeddingService.synchronize(synchronizationTargets);

        Map<Long, Map<String, float[]>> vectorsByAbstract = loadEmbeddingData(server.model()).vectorsByAbstract();
        Map<String, float[]> targetVectors = vectorsByAbstract.get(target.getSeq());
        if (targetVectors == null || targetVectors.isEmpty()) {
            throw new EmbeddingServiceException("대상 초록의 임베딩을 생성하지 못했습니다.");
        }

        List<AbstractSimilarityMatchResponse> matches = new ArrayList<>();
        Map<String, Double> sectionWeights = getDefaultWeights().asFractions();
        for (AbstractSubmissionResponse candidate : candidates) {
            if (candidate.getSeq().equals(target.getSeq())) {
                continue;
            }
            Map<String, float[]> candidateVectors = vectorsByAbstract.get(candidate.getSeq());
            SimilarityScores scores = calculateScores(
                    targetVectors,
                    candidateVectors,
                    sectionWeights
            );
            if (scores == null) {
                continue;
            }

            matches.add(AbstractSimilarityMatchResponse.builder()
                    .abstractSeq(candidate.getSeq())
                    .submissionNo(candidate.getSubmissionNo())
                    .title(candidate.getTitle())
                    .overallSimilarity(toPercentage(scores.overall()))
                    .titleSimilarity(toNullablePercentage(scores.sectionScores().get("title")))
                    .objectiveSimilarity(toNullablePercentage(scores.sectionScores().get("objective")))
                    .methodsSimilarity(toNullablePercentage(scores.sectionScores().get("methods")))
                    .resultsSimilarity(toNullablePercentage(scores.sectionScores().get("results")))
                    .conclusionsSimilarity(toNullablePercentage(scores.sectionScores().get("conclusions")))
                    .highestSection(scores.highestSection())
                    .highestSimilarity(toPercentage(scores.highestSimilarity()))
                    .build());
        }

        matches.sort(Comparator.comparingDouble(AbstractSimilarityMatchResponse::getOverallSimilarity).reversed());
        int comparedCount = matches.size();
        int topK = Math.min(Math.max(properties.getTopK(), 1), 100);
        List<AbstractSimilarityMatchResponse> topMatches = matches.stream().limit(topK).toList();

        return AbstractSimilarityResponse.builder()
                .abstractSeq(target.getSeq())
                .model(server.model())
                .dimension(server.dimension())
                .comparedCount(comparedCount)
                .matches(topMatches)
                .build();
    }

    public AbstractSimilarityResponse getStoredAnalysis(Long conferenceSeq, Long abstractSeq) {
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            throw new IllegalStateException("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }

        AbstractSubmissionResponse source = submissionRepository.findBySeq(
                conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
        );
        if (source == null) {
            throw new IllegalArgumentException("초록 정보를 찾을 수 없습니다.");
        }
        if ("draft".equals(source.getStatus())) {
            throw new IllegalStateException("임시저장 상태의 초록은 유사도 분석 대상이 아닙니다.");
        }

        List<AbstractSimilarityResult> storedResults =
                resultStorageService.findBySourceAbstractSeq(conferenceSeq, abstractSeq);
        if (storedResults.isEmpty()) {
            throw new IllegalArgumentException(
                    "저장된 유사도 분석 결과가 없습니다. 먼저 AI 유사도 분석 실행을 진행해 주세요."
            );
        }

        AbstractSimilarityResult firstResult = storedResults.get(0);
        String currentContentHash = currentContentHash(source);
        List<AbstractSimilarityMatchResponse> matches = storedResults.stream()
                .map(this::toMatchResponse)
                .toList();
        long candidateCount = submissionRepository.countSimilarityCandidates(conferenceSeq);

        return AbstractSimilarityResponse.builder()
                .abstractSeq(abstractSeq)
                .model(firstResult.getModelName())
                .dimension(properties.getDimension())
                .comparedCount((int) Math.min(Math.max(candidateCount - 1L, 0L), Integer.MAX_VALUE))
                .analyzedAt(firstResult.getAnalyzedAt())
                .stale(!currentContentHash.equals(firstResult.getSourceContentHash()))
                .matches(matches)
                .build();
    }

    public AbstractSimilarityComparisonResponse getStoredComparison(
            Long conferenceSeq,
            Long sourceAbstractSeq,
            Long targetAbstractSeq
    ) {
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            throw new IllegalStateException("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }

        AbstractSubmissionResponse source = submissionRepository.findBySeq(
                conferenceSeq, sourceAbstractSeq, personalDataProperties.requireDbEncString()
        );
        if (source == null) {
            throw new IllegalArgumentException("현재 초록 정보를 찾을 수 없습니다.");
        }
        if ("draft".equals(source.getStatus())) {
            throw new IllegalStateException("임시저장 상태의 초록은 유사도 분석 대상이 아닙니다.");
        }

        AbstractSimilarityResult storedResult = resultStorageService.findBySourceAbstractSeq(
                        conferenceSeq, sourceAbstractSeq
                )
                .stream()
                .filter(result -> targetAbstractSeq.equals(result.getTargetAbstractSeq()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("저장된 유사 초록 비교 결과를 찾을 수 없습니다."));

        AbstractSubmissionResponse target = submissionRepository.findBySeq(
                conferenceSeq, targetAbstractSeq, personalDataProperties.requireDbEncString()
        );
        if (target == null) {
            throw new IllegalArgumentException("유사 초록 정보를 찾을 수 없습니다.");
        }

        boolean stale = !currentContentHash(source).equals(storedResult.getSourceContentHash())
                || !currentContentHash(target).equals(storedResult.getTargetContentHash());

        return AbstractSimilarityComparisonResponse.builder()
                .source(toContentResponse(source))
                .target(toContentResponse(target))
                .similarity(toMatchResponse(storedResult))
                .analyzedAt(storedResult.getAnalyzedAt())
                .stale(stale)
                .build();
    }

    private AbstractSimilarityMatchResponse toMatchResponse(AbstractSimilarityResult result) {
        return AbstractSimilarityMatchResponse.builder()
                .abstractSeq(result.getTargetAbstractSeq())
                .submissionNo(result.getTargetSubmissionNo())
                .title(result.getTargetTitle())
                .overallSimilarity(result.getOverallSimilarity())
                .titleSimilarity(result.getTitleSimilarity())
                .objectiveSimilarity(result.getObjectiveSimilarity())
                .methodsSimilarity(result.getMethodsSimilarity())
                .resultsSimilarity(result.getResultsSimilarity())
                .conclusionsSimilarity(result.getConclusionsSimilarity())
                .highestSection(result.getHighestSection())
                .highestSimilarity(result.getHighestSimilarity())
                .build();
    }

    private AbstractSimilarityContentResponse toContentResponse(AbstractSubmissionResponse submission) {
        return AbstractSimilarityContentResponse.builder()
                .abstractSeq(submission.getSeq())
                .submissionNo(submission.getSubmissionNo())
                .title(submission.getTitle())
                .objectiveText(submission.getObjectiveText())
                .methodsText(submission.getMethodsText())
                .resultsText(submission.getResultsText())
                .conclusionsText(submission.getConclusionsText())
                .build();
    }

    public AbstractSimilarityDataGenerationResponse generateAnalysisData(Long conferenceSeq) {
        return generateAnalysisData(conferenceSeq, null, getDefaultWeights(), progress -> {
        });
    }

    public AbstractSimilarityDataGenerationResponse generateAnalysisData(
            Long conferenceSeq,
            Consumer<AbstractSimilarityProgress> progressConsumer
    ) {
        return generateAnalysisData(conferenceSeq, null, getDefaultWeights(), progressConsumer);
    }

    public AbstractSimilarityWeights getDefaultWeights() {
        return AbstractSimilarityWeights.from(properties.getWeights());
    }

    public AbstractSimilarityDataGenerationResponse generateAnalysisData(
            Long conferenceSeq,
            Long jobSeq,
            AbstractSimilarityWeights weights,
            Consumer<AbstractSimilarityProgress> progressConsumer
    ) {
        if (!licenseProperties.isAbstractSimilarityEnabled()) {
            throw new IllegalStateException("초록 유사도 측정 기능이 비활성화되어 있습니다.");
        }

        progressConsumer.accept(new AbstractSimilarityProgress(
                "PREPARING", 2, "분석 대상 초록을 확인하고 있습니다.", 0, 0, 0
        ));
        List<AbstractSubmissionResponse> candidates = submissionRepository.findSimilarityCandidates(conferenceSeq);
        int abstractCount = candidates.size();
        progressConsumer.accept(new AbstractSimilarityProgress(
                "EMBEDDING", 5, "벡터 서버를 확인하고 분석 데이터를 준비하고 있습니다.",
                0, 0, abstractCount
        ));
        AbstractEmbeddingService.SynchronizationResult result =
                embeddingService.synchronizeWithSummary(candidates, embeddingProgress -> {
                    int embeddingPercent = embeddingProgress.totalCount() == 0
                            ? 45
                            : 5 + (int) Math.round(
                                    40.0 * embeddingProgress.processedCount() / embeddingProgress.totalCount()
                            );
                    progressConsumer.accept(new AbstractSimilarityProgress(
                            "EMBEDDING",
                            embeddingPercent,
                            embeddingProgress.totalCount() == 0
                                    ? "모든 벡터 데이터가 최신 상태입니다."
                                    : "초록 항목을 벡터로 변환하고 있습니다.",
                            embeddingProgress.processedCount(),
                            embeddingProgress.totalCount(),
                            abstractCount
                    ));
                });
        EmbeddingHealthResponse server = result.server();
        EmbeddingData embeddingData = loadEmbeddingData(server.model());
        progressConsumer.accept(new AbstractSimilarityProgress(
                "CALCULATING", 45, "초록 간 의미 유사도를 계산하고 있습니다.",
                0, abstractCount, abstractCount
        ));
        List<AbstractSimilarityResult> similarityResults = calculateTopResults(
                candidates,
                embeddingData,
                server.model(),
                jobSeq,
                weights.asFractions(),
                progressConsumer
        );
        progressConsumer.accept(new AbstractSimilarityProgress(
                "SAVING", 90, "계산된 유사도 분석 결과를 저장하고 있습니다.",
                0, similarityResults.size(), abstractCount
        ));
        resultStorageService.replaceAll(conferenceSeq, similarityResults, storageProgress -> {
            int savingPercent = storageProgress.totalCount() == 0
                    ? 99
                    : 90 + (int) Math.round(
                            9.0 * storageProgress.processedCount() / storageProgress.totalCount()
                    );
            progressConsumer.accept(new AbstractSimilarityProgress(
                    "SAVING",
                    savingPercent,
                    "계산된 유사도 분석 결과를 저장하고 있습니다.",
                    storageProgress.processedCount(),
                    storageProgress.totalCount(),
                    abstractCount
            ));
        });

        return new AbstractSimilarityDataGenerationResponse(
                server.model(),
                server.dimension(),
                result.targetAbstractCount(),
                result.updatedAbstractCount(),
                result.generatedEmbeddingCount(),
                similarityResults.size()
        );
    }

    private EmbeddingData loadEmbeddingData(String modelName) {
        Map<Long, Map<String, float[]>> vectorsByAbstract = new HashMap<>();
        Map<Long, Map<String, String>> sectionHashesByAbstract = new HashMap<>();
        for (AbstractEmbedding embedding : embeddingRepository.findAll()) {
            if (!modelName.equals(embedding.getModelName())
                    || embedding.getDimension() == null
                    || embedding.getDimension() != properties.getDimension()
                    || embedding.getEmbedding() == null
                    || embedding.getEmbedding().length != properties.getDimension() * Float.BYTES) {
                continue;
            }
            float[] vector = EmbeddingBinaryConverter.fromBytes(embedding.getEmbedding());
            if (vector.length != properties.getDimension()) {
                continue;
            }
            vectorsByAbstract.computeIfAbsent(embedding.getAbstractSeq(), key -> new HashMap<>())
                    .put(embedding.getSectionType(), vector);
            sectionHashesByAbstract.computeIfAbsent(embedding.getAbstractSeq(), key -> new HashMap<>())
                    .put(embedding.getSectionType(), embedding.getContentHash());
        }

        Map<Long, String> contentHashesByAbstract = new HashMap<>();
        for (Map.Entry<Long, Map<String, String>> entry : sectionHashesByAbstract.entrySet()) {
            contentHashesByAbstract.put(entry.getKey(), combinedContentHash(entry.getValue()));
        }
        return new EmbeddingData(vectorsByAbstract, contentHashesByAbstract);
    }

    private List<AbstractSimilarityResult> calculateTopResults(
            List<AbstractSubmissionResponse> candidates,
            EmbeddingData embeddingData,
            String modelName,
            Long jobSeq,
            Map<String, Double> sectionWeights,
            Consumer<AbstractSimilarityProgress> progressConsumer
    ) {
        int topK = Math.min(Math.max(properties.getTopK(), 1), 100);
        List<AbstractSimilarityResult> results = new ArrayList<>(candidates.size() * topK);

        int processedSources = 0;
        int lastReportedPercent = -1;
        for (AbstractSubmissionResponse source : candidates) {
            Map<String, float[]> sourceVectors = embeddingData.vectorsByAbstract().get(source.getSeq());
            String sourceContentHash = embeddingData.contentHashesByAbstract().get(source.getSeq());
            if (sourceVectors == null || sourceVectors.isEmpty() || sourceContentHash == null) {
                throw new EmbeddingServiceException("일부 초록의 임베딩 정보를 불러오지 못했습니다.");
            }

            List<RankedSimilarity> ranked = new ArrayList<>(Math.max(candidates.size() - 1, 0));
            for (AbstractSubmissionResponse target : candidates) {
                if (source.getSeq().equals(target.getSeq())) {
                    continue;
                }
                Map<String, float[]> targetVectors = embeddingData.vectorsByAbstract().get(target.getSeq());
                SimilarityScores scores = calculateScores(sourceVectors, targetVectors, sectionWeights);
                if (scores != null) {
                    ranked.add(new RankedSimilarity(target.getSeq(), scores));
                }
            }
            ranked.sort(Comparator.comparingDouble(
                    (RankedSimilarity item) -> item.scores().overall()
            ).reversed());

            for (RankedSimilarity item : ranked.stream().limit(topK).toList()) {
                String targetContentHash = embeddingData.contentHashesByAbstract().get(item.targetAbstractSeq());
                if (targetContentHash == null) {
                    throw new EmbeddingServiceException("일부 비교 대상 초록의 임베딩 정보를 불러오지 못했습니다.");
                }
                SimilarityScores scores = item.scores();
                results.add(AbstractSimilarityResult.builder()
                        .jobSeq(jobSeq)
                        .sourceAbstractSeq(source.getSeq())
                        .targetAbstractSeq(item.targetAbstractSeq())
                        .overallSimilarity(toPercentage(scores.overall()))
                        .titleSimilarity(toNullablePercentage(scores.sectionScores().get("title")))
                        .objectiveSimilarity(toNullablePercentage(scores.sectionScores().get("objective")))
                        .methodsSimilarity(toNullablePercentage(scores.sectionScores().get("methods")))
                        .resultsSimilarity(toNullablePercentage(scores.sectionScores().get("results")))
                        .conclusionsSimilarity(toNullablePercentage(scores.sectionScores().get("conclusions")))
                        .highestSection(scores.highestSection())
                        .highestSimilarity(toPercentage(scores.highestSimilarity()))
                        .modelName(modelName)
                        .scoringVersion(SCORING_VERSION)
                        .sourceContentHash(sourceContentHash)
                        .targetContentHash(targetContentHash)
                        .build());
            }

            processedSources++;
            int calculatingPercent = candidates.isEmpty()
                    ? 90
                    : 45 + (int) Math.round(45.0 * processedSources / candidates.size());
            if (calculatingPercent != lastReportedPercent || processedSources == candidates.size()) {
                lastReportedPercent = calculatingPercent;
                progressConsumer.accept(new AbstractSimilarityProgress(
                        "CALCULATING",
                        calculatingPercent,
                        "초록 간 의미 유사도를 계산하고 있습니다.",
                        processedSources,
                        candidates.size(),
                        candidates.size()
                ));
            }
        }
        if (candidates.isEmpty()) {
            progressConsumer.accept(new AbstractSimilarityProgress(
                    "CALCULATING", 90, "분석할 제출 초록이 없습니다.", 0, 0, 0
            ));
        }
        return results;
    }

    private String combinedContentHash(Map<String, String> sectionHashes) {
        StringBuilder value = new StringBuilder();
        for (String section : SECTIONS) {
            value.append(section)
                    .append('=')
                    .append(sectionHashes.getOrDefault(section, ""))
                    .append('\n');
        }
        return sha256(value.toString());
    }

    private String currentContentHash(AbstractSubmissionResponse submission) {
        Map<String, String> sectionHashes = new HashMap<>();
        putCurrentSectionHash(sectionHashes, "title", submission.getTitle());
        putCurrentSectionHash(sectionHashes, "objective", submission.getObjectiveText());
        putCurrentSectionHash(sectionHashes, "methods", submission.getMethodsText());
        putCurrentSectionHash(sectionHashes, "results", submission.getResultsText());
        putCurrentSectionHash(sectionHashes, "conclusions", submission.getConclusionsText());
        return combinedContentHash(sectionHashes);
    }

    private void putCurrentSectionHash(Map<String, String> sectionHashes, String section, String text) {
        String normalized = normalizeText(text);
        if (!normalized.isBlank()) {
            sectionHashes.put(section, sha256(normalized));
        }
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
            return HexFormat.of().formatHex(digest.digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    private SimilarityScores calculateScores(
            Map<String, float[]> targetVectors,
            Map<String, float[]> candidateVectors,
            Map<String, Double> sectionWeights
    ) {
        if (candidateVectors == null || candidateVectors.isEmpty()) {
            return null;
        }

        Map<String, Double> sectionScores = new LinkedHashMap<>();
        double weightedScore = 0.0;
        double weightSum = 0.0;
        double highestSimilarity = -1.0;
        String highestSection = null;

        for (String section : SECTIONS) {
            float[] target = targetVectors.get(section);
            float[] candidate = candidateVectors.get(section);
            if (target == null || candidate == null) {
                continue;
            }

            double score = Math.max(0.0, Math.min(1.0, cosineSimilarity(target, candidate)));
            sectionScores.put(section, score);
            double weight = sectionWeights.getOrDefault(section, 0.0);
            weightedScore += score * weight;
            weightSum += weight;
            if (score > highestSimilarity) {
                highestSimilarity = score;
                highestSection = section;
            }
        }

        if (weightSum == 0.0 || highestSection == null) {
            return null;
        }
        return new SimilarityScores(
                sectionScores,
                weightedScore / weightSum,
                highestSection,
                highestSimilarity
        );
    }

    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Embedding dimensions do not match.");
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Double toNullablePercentage(Double score) {
        return score == null ? null : toPercentage(score);
    }

    private double toPercentage(double score) {
        return Math.round(score * 10_000.0) / 100.0;
    }

    private record SimilarityScores(
            Map<String, Double> sectionScores,
            double overall,
            String highestSection,
            double highestSimilarity
    ) {
    }

    private record RankedSimilarity(Long targetAbstractSeq, SimilarityScores scores) {
    }

    private record EmbeddingData(
            Map<Long, Map<String, float[]>> vectorsByAbstract,
            Map<Long, String> contentHashesByAbstract
    ) {
    }
}
