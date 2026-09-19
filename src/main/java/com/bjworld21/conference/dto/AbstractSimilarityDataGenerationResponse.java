package com.bjworld21.conference.dto;

public record AbstractSimilarityDataGenerationResponse(
        String model,
        Integer dimension,
        int abstractCount,
        int updatedAbstractCount,
        int generatedEmbeddingCount,
        int similarityResultCount
) {
}
