package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
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
