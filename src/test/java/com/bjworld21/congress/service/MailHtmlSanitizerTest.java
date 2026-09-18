package com.bjworld21.congress.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailHtmlSanitizerTest {
    private final MailHtmlSanitizer sanitizer = new MailHtmlSanitizer();

    @Test
    void removesExecutableHtmlAndKeepsUnsubscribePlaceholder() {
        String sanitized = sanitizer.sanitize("""
                <p onclick="alert('x')" style="color:red; text-align:center; position:fixed">안내</p>
                <script>alert('x')</script>
                <a href="{{unsubscribeUrl}}">수신 거부</a>
                """);

        assertThat(sanitized)
                .contains("안내")
                .contains("href=\"{{unsubscribeUrl}}\"")
                .contains("style=\"text-align: center\"")
                .doesNotContain("script")
                .doesNotContain("onclick")
                .doesNotContain("position")
                .doesNotContain("color");
        assertThat(sanitizer.hasVisibleContent(sanitized)).isTrue();
    }

    @Test
    void keepsOnlySafeMailFormattingAndImageAttributes() {
        String sanitized = sanitizer.sanitize("""
                <p style="font-size:18px; text-align:right">
                    <span style="color:#aabbcc; background-color:#123; font-family:'Malgun Gothic', sans-serif">내용</span>
                </p>
                <img src="https://example.com/image.png" width="640" height="320"
                     loading="lazy" style="float:left; margin-right:12px; position:fixed" onerror="alert(1)">
                <article><h4>문의</h4><a href="tel:+821012345678">전화</a></article>
                <span data-mail-safe-style="position: fixed">forged style</span>
                """);

        assertThat(sanitized)
                .contains("font-size: 18px")
                .contains("text-align: right")
                .contains("color: #AABBCC")
                .contains("background-color: #123")
                .contains("font-family: 'Malgun Gothic', sans-serif")
                .contains("width=\"640\"")
                .contains("height=\"320\"")
                .contains("loading=\"lazy\"")
                .contains("float: left")
                .contains("margin-right: 12px")
                .contains("<article>", "<h4>문의</h4>", "href=\"tel:+821012345678\"")
                .doesNotContain("data-mail-safe-style")
                .doesNotContain("position")
                .doesNotContain("onerror");
    }

    @Test
    void removesYoutubeIframeFromMailContent() {
        String sanitized = sanitizer.sanitize("""
                <p>영상 보기</p>
                <iframe src="https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"
                    width="640" height="360" allowfullscreen></iframe>
                """);

        assertThat(sanitized)
                .contains("영상 보기")
                .doesNotContain("iframe", "youtube");
    }
}
