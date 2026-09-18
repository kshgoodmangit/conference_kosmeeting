package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AbstractSimilarityProperties;
import com.bjworld21.congress.dto.EmbeddingHealthResponse;
import com.bjworld21.congress.dto.EmbeddingRequest;
import com.bjworld21.congress.dto.EmbeddingResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

@Component
public class EmbeddingClient {
    private final AbstractSimilarityProperties properties;
    private final RestClient restClient;

    public EmbeddingClient(AbstractSimilarityProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getServer().getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getServer().getReadTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getServer().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public EmbeddingHealthResponse health() {
        try {
            EmbeddingHealthResponse response = restClient.get()
                    .uri(properties.getServer().getHealthPath())
                    .retrieve()
                    .body(EmbeddingHealthResponse.class);
            if (response == null || response.status() == null || response.model() == null || response.dimension() == null) {
                throw new EmbeddingServiceException("벡터 변환 서버 상태 응답이 올바르지 않습니다.");
            }
            if (!"ok".equalsIgnoreCase(response.status()) && !"up".equalsIgnoreCase(response.status())) {
                throw new EmbeddingServiceException("벡터 변환 서버가 정상 상태가 아닙니다.");
            }
            if (response.dimension() != properties.getDimension()) {
                throw new EmbeddingServiceException("설정된 벡터 차원과 변환 서버의 차원이 다릅니다.");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new EmbeddingServiceException("벡터 변환 서버 상태 확인에 실패했습니다. HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new EmbeddingServiceException("벡터 변환 서버에 연결할 수 없습니다.", e);
        }
    }

    public EmbeddingResponse embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("벡터로 변환할 텍스트가 없습니다.");
        }

        try {
            EmbeddingResponse response = restClient.post()
                    .uri(properties.getServer().getEmbeddingsPath())
                    .body(new EmbeddingRequest(texts))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            validateResponse(response, texts.size());
            return response;
        } catch (RestClientResponseException e) {
            throw new EmbeddingServiceException("벡터 변환 요청에 실패했습니다. HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new EmbeddingServiceException("벡터 변환 서버에 연결할 수 없습니다.", e);
        }
    }

    private void validateResponse(EmbeddingResponse response, int expectedCount) {
        if (response == null || response.model() == null || response.dimension() == null
                || response.embeddings() == null || response.embeddings().size() != expectedCount) {
            throw new EmbeddingServiceException("벡터 변환 서버 응답이 올바르지 않습니다.");
        }
        if (response.dimension() != properties.getDimension()) {
            throw new EmbeddingServiceException("설정된 벡터 차원과 응답 벡터의 차원이 다릅니다.");
        }
        for (List<Double> vector : response.embeddings()) {
            if (vector == null || vector.size() != properties.getDimension()) {
                throw new EmbeddingServiceException("응답 벡터 길이가 올바르지 않습니다.");
            }
            for (Double value : vector) {
                if (value == null || !Double.isFinite(value)) {
                    throw new EmbeddingServiceException("응답 벡터에 유효하지 않은 값이 포함되어 있습니다.");
                }
            }
        }
    }
}
