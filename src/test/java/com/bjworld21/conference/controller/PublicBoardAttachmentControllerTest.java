package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.MenuSettingsResponse;
import com.bjworld21.conference.publicsite.PublicApiAdvice;
import com.bjworld21.conference.publicsite.PublicSiteTestContext;
import com.bjworld21.conference.service.BoardPostService;
import com.bjworld21.conference.service.MenuSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicBoardAttachmentControllerTest {
    private final BoardPostService boards = mock(BoardPostService.class);
    private final MenuSettingsService menus = mock(MenuSettingsService.class);
    private MockMvc mvc;
    private static final String URL = "/api/public/7/boards/1/posts/12/attachments/19?lang=ko";

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new PublicBoardAttachmentController(boards, menus))
                .addFilters(new PublicSiteTestContext()).setControllerAdvice(new PublicApiAdvice(new ObjectMapper())).build();
        when(boards.downloadPublicAttachment(7L, 1L, 12L, 19L)).thenReturn(new BoardPostService.AttachmentDownload(
                "notice.pdf", "application/pdf", 1, new ByteArrayResource(new byte[]{1})));
    }

    @Test void publicAttachmentUsesCurrentConferenceLanguageMenuAndPublishedPostService() throws Exception {
        when(menus.getActiveUserMenuTree(7L, "ko")).thenReturn(List.of(notice(false)));
        mvc.perform(get(URL)).andExpect(status().isOk());
        verify(menus).getActiveUserMenuTree(7L, "ko");
        verify(boards).downloadPublicAttachment(7L, 1L, 12L, 19L);
    }

    @Test void protectedParentBlocksAnonymousDownloadBeforeAnyPostReadOrCounterChange() throws Exception {
        var parent = MenuSettingsResponse.builder().menuKey("information").enabled(true).authRequired(true)
                .children(List.of(notice(false))).build();
        when(menus.getActiveUserMenuTree(7L, "ko")).thenReturn(List.of(parent));
        mvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        verifyNoInteractions(boards);
    }

    @Test void anotherConferencesLoginDoesNotGrantAttachmentAccess() throws Exception {
        when(menus.getActiveUserMenuTree(7L, "ko")).thenReturn(List.of(notice(true)));
        var session = new MockHttpSession();
        PublicMemberSession.signIn(session, 8, 11, "other");
        mvc.perform(get(URL).session(session)).andExpect(status().isUnauthorized());
        verifyNoInteractions(boards);
        PublicMemberSession.signIn(session, 7, 22, "correct");
        mvc.perform(get(URL).session(session)).andExpect(status().isOk());
        verify(boards).downloadPublicAttachment(7L, 1L, 12L, 19L);
    }

    @Test void missingOrDisabledMenuDoesNotExposeDownloads() throws Exception {
        when(menus.getActiveUserMenuTree(7L, "ko")).thenReturn(List.of());
        mvc.perform(get(URL)).andExpect(status().isNotFound());
        var disabled = notice(false); disabled.setEnabled(false);
        when(menus.getActiveUserMenuTree(7L, "ko")).thenReturn(List.of(disabled));
        mvc.perform(get(URL)).andExpect(status().isNotFound());
        verifyNoInteractions(boards);
    }

    @Test void untranslatedFunctionalBoardKeepsSharedPublishedAttachmentsAvailable() throws Exception {
        var untranslated = notice(false); untranslated.setTranslationReady(false);
        when(menus.getActiveUserMenuTree(7L, "ko")).thenReturn(List.of(untranslated));
        mvc.perform(get(URL)).andExpect(status().isOk());
        verify(boards).downloadPublicAttachment(7L, 1L, 12L, 19L);
    }

    @Test void explicitBoardMappingTakesPrecedenceOverLegacyMenuKey() throws Exception {
        var menu = notice(false); menu.setBoardSeq(2L);
        when(menus.getActiveUserMenuTree(7L, "ko")).thenReturn(List.of(menu));
        mvc.perform(get(URL)).andExpect(status().isNotFound());
        verifyNoInteractions(boards);
    }

    private MenuSettingsResponse notice(boolean protectedMenu) {
        return MenuSettingsResponse.builder().menuKey("notice").menuScope("user").enabled(true)
                .translationReady(true).authRequired(protectedMenu).children(List.of()).build();
    }
}
