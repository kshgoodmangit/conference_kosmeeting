package com.bjworld21.conference.service;

import com.bjworld21.conference.config.WebRiskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebRiskLinkValidatorTest {
    private WebRiskProperties properties;
    private WebRiskClient webRiskClient;
    private WebRiskLinkValidator validator;

    @BeforeEach
    void setUp() {
        properties = new WebRiskProperties();
        properties.setEnabled(true);
        webRiskClient = mock(WebRiskClient.class);
        validator = new WebRiskLinkValidator(properties, webRiskClient);
    }

    @Test
    void validatesUniqueExternalHttpLinksOnly() {
        when(webRiskClient.findThreatTypes("https://example.com/notice")).thenReturn(Set.of());

        validator.validate(
                "<a href=\"https://example.com/notice\">외부</a>"
                        + "<a href=\"https://example.com/notice\">중복</a>"
                        + "<a href=\"/internal\">내부</a>"
                        + "<a href=\"mailto:admin@example.com\">메일</a>",
                null
        );

        verify(webRiskClient).findThreatTypes("https://example.com/notice");
    }

    @Test
    void rejectsKnownDangerousLink() {
        when(webRiskClient.findThreatTypes("https://danger.example/phishing"))
                .thenReturn(Set.of("SOCIAL_ENGINEERING"));

        assertThatThrownBy(() -> validator.validate(
                "<p><a href=\"https://danger.example/phishing\">확인</a></p>",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("위험 링크")
                .hasMessageContaining("danger.example")
                .hasMessageContaining("피싱/사회공학");
    }

    @Test
    void rejectsDangerousLinkAttachedToImageAfterHtmlSanitizing() {
        String testUrl = "http://testsafebrowsing.appspot.com/s/malware.html";
        String sanitizedHtml = new CmsHtmlSanitizer().sanitize(
                "<p><a href=\"" + testUrl + "\"><img src=\"/api/admin/popups/images/sample.png\" alt=\"팝업\"></a></p>"
        );
        when(webRiskClient.findThreatTypes(testUrl)).thenReturn(Set.of("MALWARE"));

        assertThatThrownBy(() -> validator.validate(sanitizedHtml, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("위험 링크")
                .hasMessageContaining("testsafebrowsing.appspot.com")
                .hasMessageContaining("악성코드");

        verify(webRiskClient).findThreatTypes(testUrl);
    }

    @Test
    void rejectsMalformedHttpLinkWithoutCallingApi() {
        assertThatThrownBy(() -> validator.validate("<a href=\"https://\">오류</a>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("형식이 올바르지 않은 외부 링크");

        verify(webRiskClient, never()).findThreatTypes("https://");
    }

    @Test
    void skipsValidationWhenFeatureIsDisabled() {
        properties.setEnabled(false);

        validator.validate("<a href=\"https://danger.example\">외부</a>", null);

        verify(webRiskClient, never()).findThreatTypes("https://danger.example");
    }

    @Test
    void allowsSaveWhenWebRiskApiIsUnavailable() {
        String url = "https://example.com/notice";
        when(webRiskClient.findThreatTypes(url))
                .thenThrow(new WebRiskUnavailableException("Web Risk API 호출 실패"));

        assertThat(validator.validate("<a href=\"" + url + "\">외부</a>", null))
                .contains("API 검증에 실패", "링크 안전성을 확인하지 못했습니다");

        verify(webRiskClient).findThreatTypes(url);
    }

    @Test
    void returnsNoWarningWhenCheckSucceedsOrIsNotNeeded() {
        when(webRiskClient.findThreatTypes("https://example.com/")).thenReturn(Set.of());
        assertThat(validator.validate(null, "https://example.com/")).isNull();
        assertThat(validator.validate("<p>No external links</p>", null)).isNull();
        properties.setEnabled(false);
        assertThat(validator.validate(null, "https://example.com/")).isNull();
    }
}
