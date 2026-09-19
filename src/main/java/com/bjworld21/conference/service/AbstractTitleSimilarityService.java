package com.bjworld21.conference.service;

import com.bjworld21.conference.config.AbstractTitleSimilarityProperties;
import com.bjworld21.conference.dto.AbstractTitleSimilarityMatchResponse;
import com.bjworld21.conference.dto.AbstractTitleSimilarityResponse;
import com.bjworld21.conference.entity.AbstractTitleSimilarityCandidate;
import com.bjworld21.conference.entity.AbstractTitleSimilarityResult;
import com.bjworld21.conference.repository.AbstractTitleSimilarityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AbstractTitleSimilarityService {
    static final String ALGORITHM_VERSION = "title-lexical-v1";
    private static final double LEVENSHTEIN_WEIGHT = 0.20;
    private static final double TRIGRAM_WEIGHT = 0.50;
    private static final double JACCARD_WEIGHT = 0.30;

    private final AbstractTitleSimilarityProperties properties;
    private final AbstractTitleSimilarityRepository repository;

    public AbstractTitleSimilarityService(
            AbstractTitleSimilarityProperties properties,
            AbstractTitleSimilarityRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    @Transactional
    public void refreshAfterSave(Long conferenceSeq, Long abstractSeq) {
        AbstractTitleSimilarityCandidate source = repository.findSource(conferenceSeq, abstractSeq);
        if (source == null) {
            throw new IllegalArgumentException("제목 유사도를 검사할 초록을 찾을 수 없습니다.");
        }
        if ("draft".equals(source.getStatus())) {
            return;
        }

        double threshold = validatedThreshold();
        int maxMatches = Math.min(Math.max(properties.getMaxMatches(), 1), 20);
        LocalDateTime checkedAt = LocalDateTime.now();
        List<ScoredCandidate> allMatches = repository.findCandidates(conferenceSeq, abstractSeq).stream()
                .map(candidate -> new ScoredCandidate(candidate, calculate(source.getTitle(), candidate.getTitle())))
                .filter(item -> item.score().similarityScore() >= threshold)
                .sorted((left, right) -> Double.compare(
                        right.score().similarityScore(), left.score().similarityScore()
                ))
                .toList();
        List<ScoredCandidate> storedMatches = allMatches.stream().limit(maxMatches).toList();

        double maxSimilarity = allMatches.isEmpty() ? 0.0 : allMatches.get(0).score().similarityScore();
        repository.upsertCheck(
                conferenceSeq, abstractSeq, maxSimilarity, allMatches.size(), ALGORITHM_VERSION, checkedAt
        );
        Long checkSeq = repository.findCheckSeq(conferenceSeq, abstractSeq);
        if (checkSeq == null) {
            throw new IllegalStateException("제목 유사도 검사 결과를 저장하지 못했습니다.");
        }

        repository.deleteResults(abstractSeq);
        for (ScoredCandidate match : storedMatches) {
            TitleScore score = match.score();
            AbstractTitleSimilarityCandidate candidate = match.candidate();
            repository.insertResult(AbstractTitleSimilarityResult.builder()
                    .checkSeq(checkSeq)
                    .sourceAbstractSeq(abstractSeq)
                    .targetAbstractSeq(candidate.getSeq())
                    .targetSubmissionNo(candidate.getSubmissionNo())
                    .targetTitle(candidate.getTitle())
                    .similarityScore(score.similarityScore())
                    .levenshteinSimilarity(score.levenshteinSimilarity())
                    .trigramSimilarity(score.trigramSimilarity())
                    .jaccardSimilarity(score.jaccardSimilarity())
                    .exactMatch(score.exactMatch())
                    .checkedAt(checkedAt)
                    .build());
        }
    }

    public AbstractTitleSimilarityResponse getStoredResult(
            Long conferenceSeq,
            Long abstractSeq,
            Double maxSimilarity,
            Integer matchCount,
            String algorithmVersion,
            LocalDateTime checkedAt
    ) {
        List<AbstractTitleSimilarityMatchResponse> matches = repository.findResults(conferenceSeq, abstractSeq)
                .stream()
                .map(AbstractTitleSimilarityMatchResponse::from)
                .toList();
        return AbstractTitleSimilarityResponse.builder()
                .abstractSeq(abstractSeq)
                .warningThreshold(validatedThreshold())
                .maxSimilarity(maxSimilarity)
                .matchCount(matchCount == null ? matches.size() : matchCount)
                .algorithmVersion(algorithmVersion)
                .checkedAt(checkedAt)
                .matches(matches)
                .build();
    }

    public TitleScore calculate(String leftTitle, String rightTitle) {
        String leftCompact = compact(leftTitle);
        String rightCompact = compact(rightTitle);
        if (leftCompact.isEmpty() || rightCompact.isEmpty()) {
            return new TitleScore(0.0, 0.0, 0.0, 0.0, false);
        }

        boolean exactMatch = leftCompact.equals(rightCompact);
        double levenshtein = normalizedLevenshtein(leftCompact, rightCompact);
        double trigram = cosine(trigrams(leftCompact), trigrams(rightCompact));
        double jaccard = jaccard(tokens(leftTitle), tokens(rightTitle));
        double combined = exactMatch
                ? 100.0
                : levenshtein * LEVENSHTEIN_WEIGHT
                    + trigram * TRIGRAM_WEIGHT
                    + jaccard * JACCARD_WEIGHT;

        return new TitleScore(
                round(combined), round(levenshtein), round(trigram), round(jaccard), exactMatch
        );
    }

    private double validatedThreshold() {
        double threshold = properties.getWarningThreshold();
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 100.0) {
            throw new IllegalStateException("제목 유사도 경고 기준은 0부터 100 사이여야 합니다.");
        }
        return threshold;
    }

    private String compact(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private Set<String> tokens(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(List.of(normalized.split("\\s+")));
    }

    private Map<String, Integer> trigrams(String value) {
        Map<String, Integer> counts = new HashMap<>();
        if (value.length() < 3) {
            counts.put(value, 1);
            return counts;
        }
        for (int index = 0; index <= value.length() - 3; index++) {
            String part = value.substring(index, index + 3);
            counts.merge(part, 1, Integer::sum);
        }
        return counts;
    }

    private double normalizedLevenshtein(String left, String right) {
        int distance = levenshteinDistance(left, right);
        return (1.0 - (double) distance / Math.max(left.length(), right.length())) * 100.0;
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitutionCost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + substitutionCost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private double cosine(Map<String, Integer> left, Map<String, Integer> right) {
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        double dot = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            int count = entry.getValue();
            leftMagnitude += count * count;
            dot += count * right.getOrDefault(entry.getKey(), 0);
        }
        for (int count : right.values()) {
            rightMagnitude += count * count;
        }
        return leftMagnitude == 0.0 || rightMagnitude == 0.0
                ? 0.0
                : dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude)) * 100.0;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size() * 100.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record TitleScore(
            double similarityScore,
            double levenshteinSimilarity,
            double trigramSimilarity,
            double jaccardSimilarity,
            boolean exactMatch
    ) {
    }

    private record ScoredCandidate(AbstractTitleSimilarityCandidate candidate, TitleScore score) {
    }
}
