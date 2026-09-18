package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.entity.MenuSettings;
import com.bjworld21.congress.entity.MenuTranslation;
import com.bjworld21.congress.entity.MenuHtmlHistory;
import com.bjworld21.congress.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuTranslationServiceTest {
    final MenuTranslationRepository translations=mock(MenuTranslationRepository.class);
    final MenuSettingsRepository menus=mock(MenuSettingsRepository.class);
    final MenuHtmlHistoryRepository histories=mock(MenuHtmlHistoryRepository.class);
    final MenuHtmlHistoryService historyService=mock(MenuHtmlHistoryService.class);
    final ConferenceSettingsService conferences=mock(ConferenceSettingsService.class);
    final CmsHtmlSanitizer sanitizer=mock(CmsHtmlSanitizer.class);
    final MenuTranslationService service=new MenuTranslationService(translations,menus,histories,historyService,conferences,sanitizer);

    @BeforeEach void setup() {
        when(conferences.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L)
                .supportedLanguages(List.of("ko","en","ja")).defaultLanguage("en").build());
    }
    MenuSettings menu() { return MenuSettings.builder().seq(10L).conferenceSeq(1L).menuScope("user")
            .menuKey("welcome").menuName("Welcome").menuHtml("<p>English</p>").htmlRevisionNo(3L).build(); }
    MenuTranslation korean() { return MenuTranslation.builder().menuSeq(10L).languageCode("ko")
            .menuName("인사말").menuHtml("<p>국문</p>").htmlRevisionNo(2L).build(); }

    @Test void missingTranslationNeverReturnsEnglishContent() {
        when(translations.findAll(1L,"ko")).thenReturn(List.of());
        var result=service.localize(1L,List.of(menu()),"ko").get(0);
        assertThat(result.getMenuHtml()).isNull();
        assertThat(result.getTranslationReady()).isFalse();
        assertThat(result.getLanguageCode()).isEqualTo("ko");
    }

    @Test void savingKoreanKeepsEnglishAndTracksOnlyKoreanRevision() {
        when(menus.findBySeqForUpdate(1L,10L)).thenReturn(menu());
        when(translations.find(10L,"ko")).thenReturn(korean());
        service.save(1L,10L,"ko","환영 인사","<p>수정</p>",true,"국문 변경",9L);
        var saved=ArgumentCaptor.forClass(MenuTranslation.class);
        verify(translations).save(saved.capture());
        assertThat(saved.getValue().getLanguageCode()).isEqualTo("ko");
        assertThat(saved.getValue().getHtmlRevisionNo()).isEqualTo(3L);
        verify(menus,never()).update(any());
        var snapshot=ArgumentCaptor.forClass(MenuSettings.class);
        verify(historyService).record(snapshot.capture(),eq("SAVE"),isNull(),eq("국문 변경"),eq(9L));
        assertThat(snapshot.getValue().getLanguageCode()).isEqualTo("ko");
    }

    @Test void nameOnlySaveKeepsCurrentHtmlAndRevision() {
        when(menus.findBySeqForUpdate(1L,10L)).thenReturn(menu());
        when(translations.find(10L,"ko")).thenReturn(korean());
        service.save(1L,10L,"ko","새 메뉴명","stale",false,null,9L);
        var saved=ArgumentCaptor.forClass(MenuTranslation.class);
        verify(translations).save(saved.capture());
        assertThat(saved.getValue().getMenuHtml()).isEqualTo("<p>국문</p>");
        assertThat(saved.getValue().getHtmlRevisionNo()).isEqualTo(2L);
        verifyNoInteractions(historyService);
    }

    @Test void restoreCannotReadAnotherLanguageHistory() {
        when(menus.findBySeqForUpdate(1L,10L)).thenReturn(menu());
        when(translations.find(10L,"ko")).thenReturn(korean());
        assertThatThrownBy(()->service.restore(1L,10L,"ko",77L,9L,null)).isInstanceOf(ResponseStatusException.class);
        verify(histories).findLanguageHistory(10L,"ko",77L);
        verify(translations,never()).save(any());
    }

    @Test void restoreProducesNewRevisionInSameLanguageOnly() {
        when(menus.findBySeqForUpdate(1L,10L)).thenReturn(menu());
        when(translations.find(10L,"ko")).thenReturn(korean());
        when(histories.findLanguageHistory(10L,"ko",77L)).thenReturn(MenuHtmlHistory.builder().seq(77L)
                .menuSeq(10L).languageCode("ko").menuHtml("<p>이전 국문</p>").build());
        var response=service.restore(1L,10L,"ko",77L,9L,"복원");
        assertThat(response.getLanguageCode()).isEqualTo("ko");
        assertThat(response.getHtmlRevisionNo()).isEqualTo(3L);
        verify(historyService).record(any(),eq("RESTORE"),eq(77L),eq("복원"),eq(9L));
        verify(menus,never()).updateHtml(any());
    }

    @Test void rejectsUnsupportedLanguageAndForeignConferenceMenu() {
        assertThatThrownBy(()->service.save(1L,10L,"fr","x","x",true,null,9L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(1L,99L,"ko","x","x",true,null,9L)).isInstanceOf(ResponseStatusException.class);
        verify(translations,never()).save(any());
    }
}
