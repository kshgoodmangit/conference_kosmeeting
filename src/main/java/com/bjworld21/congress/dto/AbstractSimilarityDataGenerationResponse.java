package com.bjworld21.congress.dto;

public record AbstractSimilarityDataGenerationResponse(
        String model,
        Integer dimension,
        int abstractCount,
        int updatedAbstractCount,
        int generatedEmbeddingCount,
        int similarityResultCount
) {
}
