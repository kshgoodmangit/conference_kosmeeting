package com.bjworld21.conference.publicsite;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PublicSiteContextTest {
    private final PublicSiteContext site = new PublicSiteContext(7, "apdrc9", "ko", List.of("en", "ko"), "/apdrc9/ko", "/api/public/7");

    @Test
    void rebasesInternalLinksAndPreservesExternalAndStaticResources() {
        assertThat(site.pageUrl("/program-at-a-glance")).isEqualTo("/apdrc9/ko/program-at-a-glance");
        assertThat(site.pageUrl("/welcome-message")).isEqualTo("/apdrc9/ko/welcome-message");
        assertThat(site.pageUrl("/abstract-write?seq=123#form")).isEqualTo("/apdrc9/ko/abstract-write?seq=123#form");
        assertThat(site.pageUrl("/apdrc9/ko/notice?page=2")).isEqualTo("/apdrc9/ko/notice?page=2");
        assertThat(site.pageUrl("https://example.com/x")).isEqualTo("https://example.com/x");
        assertThat(site.pageUrl("/public/img/logo.svg")).isEqualTo("/public/img/logo.svg");
        assertThat(site.pageUrl("#content")).isEqualTo("#content");
    }

    @Test
    void addsContextWithoutKnowingOrRewritingPageNames() {
        assertThat(site.pageUrl("/new-custom-page?mode=full#section"))
                .isEqualTo("/apdrc9/ko/new-custom-page?mode=full#section");
        assertThat(site.pageUrl("/mypage/abstract"))
                .isEqualTo("/apdrc9/ko/mypage/abstract");
        assertThat(site.pageUrl("/apdrc8/welcome-message"))
                .isEqualTo("/apdrc9/ko/apdrc8/welcome-message");
    }

    @Test
    void scopesApisAndKeepsLanguageConsistentWithoutChangingSharedContentImagePaths() {
        assertThat(site.apiUrl("/members/login")).isEqualTo("/api/public/7/members/login?lang=ko");
        assertThat(site.apiUrl("/api/public/abstracts?seq=5&lang=en")).isEqualTo("/api/public/7/abstracts?seq=5&lang=ko");
        assertThat(site.apiUrl("/api/public/7/abstracts")).isEqualTo("/api/public/7/abstracts?lang=ko");
        assertThat(site.apiUrl("/api/popups/2/image")).isEqualTo("/api/public/7/popups/2/image?lang=ko");
        assertThat(site.apiUrl("/public/speakers/2/image")).isEqualTo("/api/public/7/speakers/2/image?lang=ko");
        assertThat(site.apiUrl("/api/boards/images/202609/file.png")).isEqualTo("/api/boards/images/202609/file.png");
    }

    @Test
    void cmsBodyLinksRetainConferenceAndLanguageWithoutAddingTemplateExecution() {
        String html = PublicContentLinks.render("<p><a href='/notice'>Notice</a><a href='https://example.com'>External</a></p>", site);
        assertThat(html).contains("href=\"/apdrc9/ko/notice\"", "href=\"https://example.com\"");
    }
}
