package com.bjworld21.conference.service;

import com.bjworld21.conference.config.WebRiskProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class GoogleWebRiskClient implements WebRiskClient {
    private static final List<String> THREAT_TYPES = List.of(
            "MALWARE",
            "SOCIAL_ENGINEERING",
            "UNWANTED_SOFTWARE"
    );

    private final WebRiskProperties properties;
    private final RestClient restClient;

    public GoogleWebRiskClient(WebRiskProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Set<String> findThreatTypes(String url) {
        String apiKey = properties.getApiKey() == null ? "" : properties.getApiKey().trim();
        if (apiKey.isEmpty()) {
            throw new WebRiskUnavailableException("Web Risk API 키가 설정되지 않아 링크를 검사할 수 없습니다.");
        }

        try {
            LookupResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/uris:search")
                            .queryParam("uri", url)
                            .queryParam("threatTypes", THREAT_TYPES.toArray())
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(LookupResponse.class);

            if (response == null || response.threat() == null || response.threat().threatTypes() == null) {
                return Set.of();
            }
            return Set.copyOf(new LinkedHashSet<>(response.threat().threatTypes()));
        } catch (RestClientResponseException exception) {
            throw new WebRiskUnavailableException(
                    "Web Risk API가 링크 검사를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new WebRiskUnavailableException(
                    "Web Risk API에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                    exception
            );
        }
    }

    public record LookupResponse(Threat threat) {
    }

    public record Threat(List<String> threatTypes) {
    }
}
