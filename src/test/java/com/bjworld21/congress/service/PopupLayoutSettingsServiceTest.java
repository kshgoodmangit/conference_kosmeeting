package com.bjworld21.congress.service;

import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopupLayoutSettingsServiceTest {

    @Mock
    private ConferenceSettingsRepository conferenceSettingsRepository;

    private PopupLayoutSettingsService service;

    @BeforeEach
    void setUp() {
        service = new PopupLayoutSettingsService(conferenceSettingsRepository);
    }

    @Test
    void getPopupLayoutNoReturnsSavedLayout() {
        when(conferenceSettingsRepository.findPopupLayoutNo(3L)).thenReturn(6);

        assertThat(service.getPopupLayoutNo(3L)).isEqualTo(6);
    }

    @Test
    void updatePopupLayoutNoPersistsValidLayout() {
        when(conferenceSettingsRepository.updatePopupLayoutNo(3L, 8)).thenReturn(1);

        assertThat(service.updatePopupLayoutNo(3L, 8)).isEqualTo(8);
        verify(conferenceSettingsRepository).updatePopupLayoutNo(3L, 8);
    }

    @Test
    void updatePopupLayoutNoRejectsOutOfRangeLayout() {
        assertThatThrownBy(() -> service.updatePopupLayoutNo(3L, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("팝업 레이아웃은 1번부터 8번까지 선택할 수 있습니다.");

        verify(conferenceSettingsRepository, never()).updatePopupLayoutNo(3L, 9);
    }

    @Test
    void updatePopupLayoutNoRejectsMissingConference() {
        when(conferenceSettingsRepository.updatePopupLayoutNo(99L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.updatePopupLayoutNo(99L, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수정할 학회 설정을 찾을 수 없습니다.");
    }
}
