package com.bjworld21.conference.service;

import com.bjworld21.conference.config.WebRiskProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WebRiskLinkValidator {
    private static final Logger log = LoggerFactory.getLogger(WebRiskLinkValidator.class);
    private static final int MAX_EXTERNAL_LINKS = 20;

    private final WebRiskProperties properties;
    private final WebRiskClient webRiskClient;

    public WebRiskLinkValidator(WebRiskProperties properties, WebRiskClient webRiskClient) {
        this.properties = properties;
        this.webRiskClient = webRiskClient;
    }

    public String validate(String sanitizedHtml, String standaloneLinkUrl) {
        if (!properties.isEnabled()) {
            return null;
        }

        Set<CheckedUrl> urls = extractUrls(sanitizedHtml, standaloneLinkUrl);
        if (urls.size() > MAX_EXTERNAL_LINKS) {
            throw new IllegalArgumentException("한 팝업에는 외부 링크를 최대 20개까지 등록할 수 있습니다.");
        }

        for (CheckedUrl checkedUrl : urls) {
            Set<String> threatTypes;
            try {
                threatTypes = webRiskClient.findThreatTypes(checkedUrl.url());
            } catch (WebRiskUnavailableException exception) {
                log.warn(
                        "Web Risk 링크 검사를 완료하지 못해 팝업 저장을 계속합니다. domain={}, reason={}",
                        checkedUrl.host(),
                        exception.getMessage()
                );
                return "Google Web Risk API 검증에 실패하여 링크 안전성을 확인하지 못했습니다.";
            }
            if (!threatTypes.isEmpty()) {
                String descriptions = threatTypes.stream()
                        .map(this::describeThreatType)
                        .sorted()
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(
                        "Google Web Risk에서 위험 링크가 감지되어 저장할 수 없습니다. 도메인: "
                                + checkedUrl.host() + ", 유형: " + descriptions
                );
            }
        }
        return null;
    }

    private Set<CheckedUrl> extractUrls(String sanitizedHtml, String standaloneLinkUrl) {
        Set<CheckedUrl> urls = new LinkedHashSet<>();
        if (sanitizedHtml != null && !sanitizedHtml.isBlank()) {
            for (Element link : Jsoup.parseBodyFragment(sanitizedHtml).select("a[href]")) {
                addIfExternalHttpUrl(urls, link.attr("href"));
            }
        }
        addIfExternalHttpUrl(urls, standaloneLinkUrl);
        return urls;
    }

    private void addIfExternalHttpUrl(Set<CheckedUrl> urls, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        String normalizedValue = value.trim();
        if (normalizedValue.startsWith("//")) {
            normalizedValue = "https:" + normalizedValue;
        }

        URI uri;
        try {
            uri = new URI(normalizedValue);
        } catch (URISyntaxException exception) {
            if (looksLikeHttpUrl(normalizedValue)) {
                throw new IllegalArgumentException("형식이 올바르지 않은 외부 링크가 포함되어 있습니다.");
            }
            return;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return;
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("형식이 올바르지 않은 외부 링크가 포함되어 있습니다.");
        }

        String url = uri.normalize().toASCIIString();
        urls.add(new CheckedUrl(url, uri.getHost().toLowerCase(Locale.ROOT)));
    }

    private boolean looksLikeHttpUrl(String value) {
        String lowerValue = value.toLowerCase(Locale.ROOT);
        return lowerValue.startsWith("http:") || lowerValue.startsWith("https:");
    }

    private String describeThreatType(String threatType) {
        return switch (threatType) {
            case "MALWARE" -> "악성코드";
            case "SOCIAL_ENGINEERING" -> "피싱/사회공학";
            case "UNWANTED_SOFTWARE" -> "원치 않는 소프트웨어";
            default -> threatType;
        };
    }

    private record CheckedUrl(String url, String host) {
    }
}
