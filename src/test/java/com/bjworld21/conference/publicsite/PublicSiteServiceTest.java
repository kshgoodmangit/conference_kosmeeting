package com.bjworld21.conference.publicsite;

import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.service.ConferenceSettingsService;
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
        for (String path : List.of("/missing_136/", "/2026_136/ja/notice", "/2026_136/../admin", "/2026_136%2fadmin/")) {
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

    @ParameterizedTest
    @CsvSource({
            "SINGLE,ko,ko,''",
            "SINGLE,both,en,/en",
            "MULTI,ko,ko,/apdrc8",
            "MULTI,both,en,/apdrc8/en"
    })
    void languageLikePageNamesRemainPagesInEveryMode(String mode, String languages, String language, String base) {
        var sites = sites(mode, languages);
        for (String page : List.of("/on-site", "/qr-code", "/ci-guide", "/fr", "/zh-hans")) {
            var result = sites.resolvePage(base + page);
            assertThat(result.pagePath()).isEqualTo(page);
            assertThat(result.context().language()).isEqualTo(language);
            assertThat(result.redirectUrl()).isNull();
        }
    }

    @Test
    void onlyConfiguredLanguagePrefixesSelectTheLanguage() {
        var sites = sites("MULTI", "both");
        var page = sites.resolvePage("/apdrc8/on-site");
        assertThat(page.pagePath()).isEqualTo("/on-site");
        assertThat(page.redirectUrl()).isEqualTo("/apdrc8/en/on-site");
        assertThat(sites.resolvePage("/apdrc8/fr").pagePath()).isEqualTo("/fr");
        var koreanHome = sites.resolvePage("/apdrc8/ko");
        assertThat(koreanHome.pagePath()).isEqualTo("/");
        assertThat(koreanHome.context().language()).isEqualTo("ko");
        assertThat(koreanHome.redirectUrl()).isEqualTo("/apdrc8/ko/");
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

    @ParameterizedTest
    @CsvSource({
            "ko,false,/2026_136/",
            "en,false,/2026_136/",
            "ko,true,/2026_136/ko/",
            "en,true,/2026_136/en/"
    })
    void multiRootRedirectsToHighestSeqUsingItsDefaultLanguage(String language, boolean bilingual, String url) {
        var settings = mock(ConferenceSettingsService.class);
        var older = ConferenceSettingsResponse.builder().published(true).seq(1L).sitePath("older")
                .eventStartDate(java.time.LocalDate.of(2028, 1, 1))
                .defaultLanguage("en").supportedLanguages(List.of("en")).build();
        var newest = ConferenceSettingsResponse.builder().published(true).seq(3L).sitePath("2026_136")
                .eventStartDate(java.time.LocalDate.of(2026, 1, 1))
                .defaultLanguage(language).supportedLanguages(bilingual ? List.of("ko", "en") : List.of(language)).build();
        when(settings.getSettingsList()).thenReturn(List.of(older, newest));
        when(settings.getSettingsBySitePath("older")).thenReturn(older);
        var sites = new PublicSiteService(settings, new PublicSiteProperties());

        var root = sites.resolvePage("/");

        assertThat(root.redirectUrl()).isEqualTo(url);
        assertThat(root.context().conferenceSeq()).isEqualTo(3L);
        assertThat(root.context().language()).isEqualTo(language);
        assertThat(root.pagePath()).isEqualTo("/");
        var olderHome = sites.resolvePage("/older/");
        assertThat(olderHome.context().conferenceSeq()).isEqualTo(1L);
        assertThat(olderHome.redirectUrl()).isNull();
    }

    @Test
    void multiRootKeepsTheEmptyDirectoryWhenNoConferencesExist() {
        var settings = mock(ConferenceSettingsService.class);
        when(settings.getSettingsList()).thenReturn(List.of());
        var root = new PublicSiteService(settings, new PublicSiteProperties()).resolvePage("/");
        assertThat(root.context()).isNull();
        assertThat(root.redirectUrl()).isNull();
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
        when(settings.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().published(true).seq(1L).sitePath("apdrc8")
                .defaultLanguage("ja").supportedLanguages(List.of("ko", "en")).build());
        var sites = new PublicSiteService(settings, new PublicSiteProperties());
        assertThatThrownBy(() -> sites.resolveApi(1L, null)).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void rootSkipsNewerUnpublishedConferencesAndKeepsTheLatestPublishedOne() {
        var settings = mock(ConferenceSettingsService.class);
        var visible = ConferenceSettingsResponse.builder().seq(2L).sitePath("visible")
                .published(true).defaultLanguage("ko").supportedLanguages(List.of("ko")).build();
        var hidden = ConferenceSettingsResponse.builder().seq(9L).sitePath("hidden")
                .published(false).defaultLanguage("en").supportedLanguages(List.of("en")).build();
        when(settings.getSettingsList()).thenReturn(List.of(hidden, visible));
        var sites = new PublicSiteService(settings, new PublicSiteProperties());
        assertThat(sites.resolvePage("/").redirectUrl()).isEqualTo("/visible/");
        assertThat(sites.getPublishedConferences()).containsExactly(visible);

        when(settings.getSettingsList()).thenReturn(List.of(hidden));
        assertThat(sites.resolvePage("/").context()).isNull();
        assertThat(sites.getPublishedConferences()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"SINGLE", "MULTI"})
    void unpublishedConferencesRejectDirectPagesAndPublicApis(String mode) {
        for (Boolean published : new Boolean[]{false, null}) {
            var settings = mock(ConferenceSettingsService.class);
            var hidden = ConferenceSettingsResponse.builder().seq(1L).sitePath("hidden")
                    .published(published).defaultLanguage("en").supportedLanguages(List.of("en")).build();
            when(settings.getSettings(1L)).thenReturn(hidden);
            when(settings.getSettingsBySitePath("hidden")).thenReturn(hidden);
            var properties = new PublicSiteProperties();
            properties.setConferenceMode(PublicSiteProperties.ConferenceMode.valueOf(mode));
            properties.setDefaultConferenceSeq(1L);
            var sites = new PublicSiteService(settings, properties);
            assertThatThrownBy(() -> sites.resolvePage("MULTI".equals(mode) ? "/hidden/" : "/"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
            assertThatThrownBy(() -> sites.resolveApi(1L, "en"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        }
    }
    private PublicSiteService sites(String mode, String languages) {
        return sites(mode, languages, "apdrc8");
    }

    private PublicSiteService sites(String mode, String languages, String sitePath) {
        var properties = new PublicSiteProperties();
        properties.setConferenceMode(PublicSiteProperties.ConferenceMode.valueOf(mode));
        properties.setDefaultConferenceSeq(1L);
        var settings = mock(ConferenceSettingsService.class);
        var conference = ConferenceSettingsResponse.builder().published(true).seq(1L).sitePath(sitePath)
                .defaultLanguage("both".equals(languages) ? "en" : languages)
                .supportedLanguages("both".equals(languages) ? List.of("ko", "en") : List.of(languages)).build();
        when(settings.getSettings(1L)).thenReturn(conference);
        when(settings.getSettingsBySitePath(sitePath)).thenReturn(conference);
        return new PublicSiteService(settings, properties);
    }
}
