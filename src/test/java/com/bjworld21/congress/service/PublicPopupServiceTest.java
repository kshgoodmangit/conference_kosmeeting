package com.bjworld21.congress.service;

import com.bjworld21.congress.entity.Popup;
import com.bjworld21.congress.repository.PopupRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublicPopupServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T14:59:30Z"), ZoneOffset.UTC);
    private final PopupRepository repository = mock(PopupRepository.class);
    private final PopupLayoutSettingsService layouts = mock(PopupLayoutSettingsService.class);
    private final PublicPopupPreferences preferences = new PublicPopupPreferences(clock);
    private final PublicPopupService service = new PublicPopupService(repository, layouts, preferences, new CmsHtmlSanitizer(), clock);

    @Test
    void hidingAllSkipsBothQueriesEvenWhenNewPopupsOrLayoutsAreAdded() {
        var cookie = preferences.hideToday(7L, true);
        assertThat(service.forHome(7L, new Cookie[]{new Cookie(cookie.getName(), cookie.getValue())})).isNull();
        verifyNoInteractions(repository, layouts);
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(30);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void cookieExpiresAtKoreanMidnightAndIsScopedToConference() {
        Cookie today = new Cookie("conference_popups_hidden_7", "2026-09-15");
        assertThat(preferences.isHidden(7L, new Cookie[]{today})).isTrue();
        assertThat(preferences.isHidden(8L, new Cookie[]{today})).isFalse();
        assertThat(preferences.isHidden(7L, null)).isFalse();
        assertThat(preferences.isHidden(7L, new Cookie[]{new Cookie(today.getName(), "invalid")})).isFalse();
        assertThat(preferences.isHidden(7L, new Cookie[]{new Cookie(today.getName(), "2026-09-16")})).isFalse();
        var nextDay = new PublicPopupPreferences(Clock.offset(clock, java.time.Duration.ofSeconds(30)));
        assertThat(nextDay.isHidden(7L, new Cookie[]{today})).isFalse();
        assertThat(nextDay.hideToday(7L, false).getMaxAge().getSeconds()).isEqualTo(86400);
    }

    @Test
    void manualOpenKeepsDailySuppressionForLaterHomeVisits() {
        var cookie = preferences.hideToday(7L, true);
        var cookies = new Cookie[]{new Cookie(cookie.getName(), cookie.getValue())};
        var popup = Popup.builder().seq(1L).title("Notice").content("<p>Published notice</p>").build();
        when(repository.findVisible(7L, LocalDate.of(2026, 9, 15))).thenReturn(List.of(popup));
        when(layouts.getPopupLayoutNo(7L)).thenReturn(5);

        assertThat(service.forHome(7L, cookies)).isNull();
        assertThat(service.forManualOpen(7L).items()).hasSize(1);
        assertThat(service.forHome(7L, cookies)).isNull();
        verify(repository, times(1)).findVisible(7L, LocalDate.of(2026, 9, 15));
        assertThat(preferences.isHidden(7L, cookies)).isTrue();
    }

    @Test
    void rendersWholeSanitizedBodyAndFallsBackToLegacyImage() {
        var body = Popup.builder().seq(1L).title("Conference notice")
                .content("<p class='header'>Text before image</p><img src='/api/popups/images/202609/a.png' onerror='bad()'>"
                        + "<p>Text after image</p><script>bad()</script>")
                .linkUrl("javascript:bad()").build();
        var image = Popup.builder().seq(2L).title("Legacy image").popupImageSaveFilename("202609/a.png")
                .content("<p>&nbsp;</p>").linkUrl("/registration/online-registration").build();
        var empty = Popup.builder().seq(3L).title("Empty").content("<p><br></p>").build();
        when(repository.findVisible(7L, LocalDate.of(2026, 9, 15))).thenReturn(List.of(body, image, empty));
        when(layouts.getPopupLayoutNo(7L)).thenReturn(7);
        var display = service.forHome(7L, null);
        assertThat(display.layoutNo()).isEqualTo(7);
        assertThat(display.items()).hasSize(2);
        assertThat(display.items().get(0).contentHtml()).contains("Text before image", "Text after image", "/api/popups/images/202609/a.png")
                .doesNotContain("script", "onerror", "class=");
        assertThat(display.items().get(0).linkUrl()).isNull();
        assertThat(display.items().get(1).imageUrl()).isEqualTo("/api/popups/2/image");
        assertThat(display.items().get(1).linkUrl()).isEqualTo("/registration/online-registration");
    }

    @Test
    void expiredCookieAllowsQueryUsingKoreanDateRatherThanUtcDate() {
        var nextDayClock = Clock.offset(clock, java.time.Duration.ofMinutes(1));
        var nextDay = new PublicPopupService(repository, layouts, new PublicPopupPreferences(nextDayClock), new CmsHtmlSanitizer(), nextDayClock);
        when(repository.findVisible(7L, LocalDate.of(2026, 9, 16))).thenReturn(List.of());
        assertThat(nextDay.forHome(7L, new Cookie[]{new Cookie("conference_popups_hidden_7", "2026-09-15")})).isNull();
        verify(repository).findVisible(7L, LocalDate.of(2026, 9, 16));
        verifyNoInteractions(layouts);
    }

    @Test
    void popupFailuresDoNotBreakHomeRendering() {
        when(repository.findVisible(anyLong(), any())).thenThrow(new IllegalStateException("unavailable"));
        assertThat(service.forHome(7L, null)).isNull();
        verifyNoInteractions(layouts);
    }
}
