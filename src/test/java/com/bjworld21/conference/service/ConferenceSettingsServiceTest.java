package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.entity.ConferenceSettings;
import com.bjworld21.conference.repository.ConferenceSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConferenceSettingsServiceTest {

    @Test
    void sitePathLookupDoesNotFallbackToLatestConference() {
        when(repository.findBySitePath("missing")).thenReturn(null);
        assertThatThrownBy(() -> service.getSettingsBySitePath("missing"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(repository, never()).findLatest();
    }

    @Test
    void savesUnderscoreSitePathWithoutRewritingTheConferenceUrl() {
        when(repository.findBySeq(1L)).thenReturn(ConferenceSettings.builder().seq(1L).build());
        when(repository.findLanguages(1L)).thenReturn(List.of("ko", "en"));
        var response = service.savePublicSite(1L, "2026_136", "en", List.of("ko", "en"));
        assertThat(response.getSitePath()).isEqualTo("2026_136");
        ArgumentCaptor<ConferenceSettings> captor = ArgumentCaptor.forClass(ConferenceSettings.class);
        verify(repository).updatePublicSite(captor.capture());
        assertThat(captor.getValue().getSitePath()).isEqualTo("2026_136");
    }

    @Test
    void rejectsEmptyLanguagesAndDefaultOutsideSupportedList() {
        when(repository.findBySeq(1L)).thenReturn(ConferenceSettings.builder().seq(1L).build());
        assertThatThrownBy(() -> service.savePublicSite(1L, "apdrc8", "en", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.savePublicSite(1L, "apdrc8", "en", List.of("ko")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).updatePublicSite(any());
        verify(repository, never()).deleteLanguages(any());
    }

    @Test
    void rejectsDuplicateSitePathAndLanguageCodes() {
        when(repository.findBySeq(1L)).thenReturn(ConferenceSettings.builder().seq(1L).build());
        when(repository.findBySitePath("apdrc9")).thenReturn(ConferenceSettings.builder().seq(2L).build());
        assertThatThrownBy(() -> service.savePublicSite(1L, "apdrc9", "en", List.of("en")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("학회 경로");
        assertThatThrownBy(() -> service.savePublicSite(1L, "apdrc8", "en", List.of("en", "EN")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("중복");
    }

    @Test
    void supportsAdditionalLanguageCodesAndReturnsConfiguredList() {
        when(repository.findBySeq(1L)).thenReturn(ConferenceSettings.builder().seq(1L).build());
        when(repository.findLanguages(1L)).thenReturn(List.of("ko", "zh-Hans"));
        var response=service.savePublicSite(1L, "APDRC8", "zh-hans", List.of("ko", "zh-Hans"));
        assertThat(response.getSitePath()).isEqualTo("apdrc8");
        assertThat(response.getDefaultLanguage()).isEqualTo("zh-Hans");
        assertThat(response.getSupportedLanguages()).containsExactly("ko", "zh-Hans");
        verify(repository).insertLanguage(1L, "zh-Hans", 1);
    }

    @Mock
    private ConferenceSettingsRepository repository;

    private ConferenceSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ConferenceSettingsService(repository);
    }

    @Test
    void returnsEveryConferenceSettingInRepositoryOrder() {
        when(repository.findAll()).thenReturn(List.of(
                ConferenceSettings.builder().seq(2L).eventName("ICMS 2027").build(),
                ConferenceSettings.builder().seq(1L).eventName("ICMS 2026").build()
        ));

        List<ConferenceSettingsResponse> result = service.getSettingsList();

        assertThat(result).extracting(ConferenceSettingsResponse::getEventName)
                .containsExactly("ICMS 2027", "ICMS 2026");
    }

    @Test
    void addingConferenceAlwaysInsertsANewRow() {
        service.saveSettings(
                " ICMS 2027 ",
                LocalDate.of(2027, 5, 1),
                LocalDate.of(2027, 5, 3),
                null,
                null,
                null,
                null,
                "usd",
                null,
                null,
                null,
                null,
                " Seoul "
        );

        ArgumentCaptor<ConferenceSettings> captor = ArgumentCaptor.forClass(ConferenceSettings.class);
        verify(repository).insert(captor.capture());
        verify(repository, never()).update(any());
        assertThat(captor.getValue().getSeq()).isNull();
        assertThat(captor.getValue().getEventName()).isEqualTo("ICMS 2027");
        assertThat(captor.getValue().getRegistrationCurrency()).isEqualTo("USD");
        assertThat(captor.getValue().getVenueAddress()).isEqualTo("Seoul");
    }

    @Test
    void updatesOnlyTheRequestedConference() {
        when(repository.findBySeq(7L)).thenReturn(ConferenceSettings.builder()
                .seq(7L)
                .eventName("Before")
                .build());

        ConferenceSettingsResponse result = service.updateSettings(
                7L,
                "After",
                null,
                null,
                null,
                null,
                null,
                null,
                "KRW",
                null,
                null,
                null,
                null,
                null
        );

        ArgumentCaptor<ConferenceSettings> captor = ArgumentCaptor.forClass(ConferenceSettings.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().getSeq()).isEqualTo(7L);
        assertThat(captor.getValue().getEventName()).isEqualTo("After");
        assertThat(result.getMessage()).isEqualTo("학회 설정이 수정되었습니다.");
    }

    @Test
    void rejectsUpdateWhenConferenceDoesNotExist() {
        when(repository.findBySeq(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateSettings(
                99L,
                "Missing",
                null,
                null,
                null,
                null,
                null,
                null,
                "USD",
                null,
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수정할 학회 설정을 찾을 수 없습니다.");
    }
}
