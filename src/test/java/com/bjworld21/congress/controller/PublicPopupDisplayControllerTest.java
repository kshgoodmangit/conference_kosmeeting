package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.PublicPopupDisplay;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.bjworld21.congress.service.PublicPopupService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicPopupDisplayControllerTest {
    @Test
    void manualDisplayIgnoresSuppressionWithoutSettingOrDeletingCookies() throws Exception {
        var conferences = mock(ConferenceSettingsService.class);
        var popups = mock(PublicPopupService.class);
        when(conferences.getLatestConferenceSeq()).thenReturn(7L);
        var display = new PublicPopupDisplay(5, List.of(
                new PublicPopupDisplay.Item(1L, "Notice", "<p>Published</p>", null, null)));
        var site = new com.bjworld21.congress.publicsite.PublicSiteContext(7L, "apdrc8", "en", List.of("en"), "/apdrc8", "/api/public/7");
        when(popups.forManualOpen(site)).thenReturn(display);
        var mvc = MockMvcBuilders.standaloneSetup(new PublicPopupDisplayController(conferences, popups)).build();

        mvc.perform(get("/api/public/7/popups/display").requestAttr(com.bjworld21.congress.publicsite.PublicSiteContext.ATTRIBUTE, new com.bjworld21.congress.publicsite.PublicSiteContext(7L, "apdrc8", "en", List.of("en"), "/apdrc8", "/api/public/7")).cookie(new Cookie("conference_popups_hidden_7", "2026-09-15")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(view().name("public/popups :: panel"))
                .andExpect(model().attribute("popupDisplay", display));
        verify(popups).forManualOpen(site);
        verifyNoMoreInteractions(popups);
    }

    @Test
    void noVisiblePopupsReturnsNoContent() throws Exception {
        var conferences = mock(ConferenceSettingsService.class);
        var popups = mock(PublicPopupService.class);
        when(conferences.getLatestConferenceSeq()).thenReturn(7L);
        var mvc = MockMvcBuilders.standaloneSetup(new PublicPopupDisplayController(conferences, popups)).build();
        mvc.perform(get("/api/public/7/popups/display").requestAttr(com.bjworld21.congress.publicsite.PublicSiteContext.ATTRIBUTE, new com.bjworld21.congress.publicsite.PublicSiteContext(7L, "apdrc8", "en", List.of("en"), "/apdrc8", "/api/public/7")))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string(""));
    }
}
