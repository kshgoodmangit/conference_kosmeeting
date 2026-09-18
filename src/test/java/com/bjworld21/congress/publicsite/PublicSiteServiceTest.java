package com.bjworld21.congress.publicsite;

import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.service.ConferenceSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicSiteServiceTest {
    @ParameterizedTest
    @CsvSource({
            "SINGLE,ko,/welcome-message,ko,''",
            "SINGLE,en,/welcome-message,en,''",
            "SINGLE,both,/ko/welcome-message,ko,/ko",
            "SINGLE,both,/en/welcome-message,en,/en",
            "MULTI,ko,/apdrc8/welcome-message,ko,/apdrc8",
            "MULTI,en,/apdrc8/welcome-message,en,/apdrc8",
            "MULTI,both,/apdrc8/ko/welcome-message,ko,/apdrc8/ko",
            "MULTI,both,/apdrc8/en/welcome-message,en,/apdrc8/en"
    })
    void resolvesEverySupportedMode(String mode, String languages, String url, String language, String base) {
        var sites = sites(mode, languages);
        var result = sites.resolvePage(url);
        assertThat(result.redirectUrl()).isNull();
        assertThat(result.pagePath()).isEqualTo("/welcome-message");
        assertThat(result.context().conferenceSeq()).isEqualTo(1L);
        assertThat(result.context().language()).isEqualTo(language);
        assertThat(result.context().siteBasePath()).isEqualTo(base);
        assertThat(result.context().apiBasePath()).isEqualTo("/api/public/1");
    }

    @Test
    void supportsUnderscoreConferencePathsForHomePagesAndApiContext() {
        var sites = sites("MULTI", "both", "2026_136");
        var home = sites.resolvePage("/2026_136/");
        assertThat(home.redirectUrl()).isEqualTo("/2026_136/en/");
        assertThat(home.context().conferenceSeq()).isEqualTo(1L);
        assertThat(sites.resolvePage("/2026_136/en/").redirectUrl()).isNull();
        assertThat(sites.resolvePage("/2026_136/ko/welcome-message").context().language()).isEqualTo("ko");
        assertThat(sites.resolveApi(1L, "en").siteBasePath()).isEqualTo("/2026_136/en");
        assertThat(sites.resolvePage("/2026_136/en/notice-detail").pagePath()).isEqualTo("/notice-detail");
        assertThat(sites("MULTI", "ko", "2026_136").resolvePage("/2026_136/").redirectUrl()).isNull();
        for (String path : List.of("/missing_136/", "/2026_136/ja/", "/2026_136/../admin", "/2026_136%2fadmin/")) {
            assertThatThrownBy(() -> sites.resolvePage(path)).isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        }
    }

    @Test
    void redirectsMissingLanguageAndRedundantSingleLanguageToOneCanonicalUrl() {
        assertThat(sites("MULTI", "both").resolvePage("/apdrc8/welcome-message").redirectUrl())
                .isEqualTo("/apdrc8/en/welcome-message");
        assertThat(sites("SINGLE", "both").resolvePage("/welcome-message").redirectUrl())
                .isEqualTo("/en/welcome-message");
        assertThat(sites("SINGLE", "ko").resolvePage("/ko/welcome-message").redirectUrl())
                .isEqualTo("/welcome-message");
        assertThat(sites("MULTI", "ko").resolvePage("/apdrc8/ko/").redirectUrl()).isEqualTo("/apdrc8/");
    }

    @Test
    void rejectsOldNestedPathsInsteadOfSilentlyTranslatingPageNames() {
        var sites = sites("MULTI", "both");
        for (String path : List.of("/apdrc8/en/program/program-at-a-glance",
                "/apdrc8/ko/information/noticedetail/12", "/apdrc8/ko/mypage/abstract/write")) {
            assertThatThrownBy(() -> sites.resolvePage(path)).isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        }
        assertThat(sites.resolvePage("/apdrc8/ko/abstract-write").pagePath()).isEqualTo("/abstract-write");
        assertThat(sites.resolvePage("/apdrc8/en/notice-detail").pagePath()).isEqualTo("/notice-detail");
    }

    @Test
    void rejectsUnknownConferenceUnsupportedLanguageAndMalformedPaths() {
        var sites = sites("MULTI", "both");
        for (String path : List.of("/missing/en/notice", "/apdrc8/ja/notice", "/apdrc8/en/unknown/nested",
                "/api/public/1/abstracts", "/admin/menu", "/apdrc8//en/notice", "/apdrc8/en/%2e%2e/admin")) {
            assertThatThrownBy(() -> sites.resolvePage(path)).isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        }
    }

    @Test
    void multiRootIsADirectoryAndNeverSelectsLatestConference() {
        assertThat(sites("MULTI", "both").resolvePage("/").context()).isNull();
    }

    @Test
    void singleModeRejectsOtherConferenceApiAndUsesConfiguredDefaultLanguage() {
        var sites = sites("SINGLE", "ko");
        assertThat(sites.resolveApi(1L, null).language()).isEqualTo("ko");
        assertThatThrownBy(() -> sites.resolveApi(2L, "ko")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sites.resolveApi(1L, "en")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void incompleteLanguageConfigurationFailsInsteadOfServingWrongLanguage() {
        var settings = mock(ConferenceSettingsService.class);
        when(settings.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("apdrc8")
                .defaultLanguage("ja").supportedLanguages(List.of("ko", "en")).build());
        var sites = new PublicSiteService(settings, new PublicSiteProperties());
        assertThatThrownBy(() -> sites.resolveApi(1L, null)).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(503));
    }

    private PublicSiteService sites(String mode, String languages) {
        return sites(mode, languages, "apdrc8");
    }

    private PublicSiteService sites(String mode, String languages, String sitePath) {
        var properties = new PublicSiteProperties();
        properties.setConferenceMode(PublicSiteProperties.ConferenceMode.valueOf(mode));
        properties.setDefaultConferenceSeq(1L);
        var settings = mock(ConferenceSettingsService.class);
        var conference = ConferenceSettingsResponse.builder().seq(1L).sitePath(sitePath)
                .defaultLanguage("both".equals(languages) ? "en" : languages)
                .supportedLanguages("both".equals(languages) ? List.of("ko", "en") : List.of(languages)).build();
        when(settings.getSettings(1L)).thenReturn(conference);
        when(settings.getSettingsBySitePath(sitePath)).thenReturn(conference);
        return new PublicSiteService(settings, properties);
    }
}
