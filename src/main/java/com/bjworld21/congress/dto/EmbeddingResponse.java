package com.bjworld21.congress.dto;

import java.util.List;

public record EmbeddingResponse(String model, Integer dimension, List<List<Double>> embeddings) {
}
