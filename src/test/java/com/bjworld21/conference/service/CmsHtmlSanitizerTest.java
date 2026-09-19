package com.bjworld21.conference.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CmsHtmlSanitizerTest {
    private final CmsHtmlSanitizer sanitizer = new CmsHtmlSanitizer();

    @Test
    void removesExecutableMarkupAndKeepsPublishingMarkup() {
        String sanitized = sanitizer.sanitize("""
                <section class="guide"><h2>Guide</h2><script>alert('x')</script>
                <a href="javascript:alert('x')">unsafe</a><table><tr><td>Value</td></tr></table></section>
                """);

        assertThat(sanitized)
                .contains("<section class=\"guide\">", "<h2>Guide</h2>", "<table>")
                .doesNotContain("<script", "javascript:");
    }

    @Test
    void keepsUploadedHttpImageAndRemovesInlineDataImage() {
        String sanitized = sanitizer.sanitize("""
                <p><img src="https://conference.example/api/boards/images/sample.png" alt="program"
                    align="left" loading="lazy" style="float:left; width:640px; margin-right:12px; position:fixed"></p>
                <p><img src="data:image/png;base64,unsafe" onerror="alert('x')"></p>
                """);

        assertThat(sanitized)
                .contains("https://conference.example/api/boards/images/sample.png")
                .contains("align=\"left\"", "loading=\"lazy\"", "float: left", "width: 640px", "margin-right: 12px")
                .doesNotContain("data:image", "onerror", "position");
    }

    @Test
    void keepsOnlyCommonTextStylesAndSafeLinkSchemes() {
        String sanitized = sanitizer.sanitize("""
                <p id="notice" style="text-align:center; color:#1a2b3c; font-size:16px;
                position:fixed; background-image:url(javascript:alert('x'))">Notice</p>
                <p data-cms-safe-style="position: fixed">forged style</p>
                <a href="ftp://files.example.com/file" target="_blank" onclick="alert('x')">file</a>
                <a href="https://conference.example/program" target="_blank">program</a>
                <a href="https://conference.example/invalid" target="preview">invalid target</a>
                """);

        assertThat(sanitized)
                .contains("style=\"text-align: center; color: #1A2B3C; font-size: 16px\"")
                .contains("href=\"https://conference.example/program\"")
                .contains("rel=\"noopener noreferrer\"")
                .doesNotContain("id=", "data-cms-safe-style", "position", "background-image",
                        "javascript:", "ftp:", "onclick", "target=\"preview\"");
    }

    @Test
    void keepsOnlyNormalizedYoutubeEmbedIframes() {
        String sanitized = sanitizer.sanitize("""
                <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ" width="9999" height="9999"
                    title="changed" style="position:fixed" onload="alert('x')" allow="camera"></iframe>
                <iframe src="https://evil.example/embed/dQw4w9WgXcQ"></iframe>
                <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1"></iframe>
                <iframe src="javascript:alert('x')"></iframe>
                """);

        assertThat(sanitized)
                .contains("src=\"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ\"")
                .contains("width=\"640\"", "height=\"360\"", "title=\"YouTube 영상\"")
                .contains("loading=\"lazy\"", "referrerpolicy=\"strict-origin-when-cross-origin\"")
                .contains("allow=\"encrypted-media; picture-in-picture\"", "allowfullscreen")
                .doesNotContain("evil.example", "autoplay", "javascript:", "position", "onload", "camera", "9999");
        assertThat(Jsoup.parseBodyFragment(sanitized).select("iframe")).hasSize(1);
    }
}
