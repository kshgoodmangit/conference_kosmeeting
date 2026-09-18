package com.bjworld21.congress.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractSimilarityWeightsTest {

    @Test
    void convertsPercentagesToScoringFractions() {
        AbstractSimilarityWeights weights = new AbstractSimilarityWeights(10, 20, 20, 30, 20);

        assertThat(weights.asFractions())
                .containsEntry("title", 0.10)
                .containsEntry("results", 0.30)
                .containsEntry("conclusions", 0.20);
    }

    @Test
    void rejectsWeightsWhoseTotalIsNotOneHundredPercent() {
        assertThatThrownBy(() -> new AbstractSimilarityWeights(10, 20, 20, 20, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유사도 가중치의 합계는 100%여야 합니다.");
    }

    @Test
    void rejectsWeightsOutsideTheAllowedRange() {
        assertThatThrownBy(() -> new AbstractSimilarityWeights(-1, 21, 20, 30, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("제목 가중치는 0%에서 100% 사이여야 합니다.");
    }
}
