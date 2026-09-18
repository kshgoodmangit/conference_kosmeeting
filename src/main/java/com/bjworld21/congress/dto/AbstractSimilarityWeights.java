package com.bjworld21.congress.dto;

import com.bjworld21.congress.config.AbstractSimilarityProperties;

import java.util.Map;

public record AbstractSimilarityWeights(
        int title,
        int objective,
        int methods,
        int results,
        int conclusions
) {
    public AbstractSimilarityWeights {
        validateRange("제목", title);
        validateRange("Objective", objective);
        validateRange("Methods", methods);
        validateRange("Results", results);
        validateRange("Conclusions", conclusions);
        if (title + objective + methods + results + conclusions != 100) {
            throw new IllegalArgumentException("유사도 가중치의 합계는 100%여야 합니다.");
        }
    }

    public static AbstractSimilarityWeights from(AbstractSimilarityProperties.Weights weights) {
        return new AbstractSimilarityWeights(
                weights.getTitle(),
                weights.getObjective(),
                weights.getMethods(),
                weights.getResults(),
                weights.getConclusions()
        );
    }

    public Map<String, Double> asFractions() {
        return Map.of(
                "title", title / 100.0,
                "objective", objective / 100.0,
                "methods", methods / 100.0,
                "results", results / 100.0,
                "conclusions", conclusions / 100.0
        );
    }

    private static void validateRange(String label, int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(label + " 가중치는 0%에서 100% 사이여야 합니다.");
        }
    }
}
