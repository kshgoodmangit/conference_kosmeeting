package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.AdminSessionInterceptor;
import com.bjworld21.congress.dto.MenuSettingsResponse;
import com.bjworld21.congress.service.MenuHtmlHistoryService;
import com.bjworld21.congress.service.MenuSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MenuHtmlHistoryControllerTest {
    @Test void languageHistoryIsPassedToScopedServiceAndRejectsInvalidLanguage() throws Exception {
        var translations=mock(com.bjworld21.congress.service.MenuTranslationService.class);
        var controller=new MenuSettingsController(menus,histories);
        controller.setTranslationService(translations);
        var languageMvc=MockMvcBuilders.standaloneSetup(controller).addInterceptors(new AdminSessionInterceptor()).build();
        when(translations.list(1L,2L,"ko",0,20)).thenReturn(new MenuHtmlHistoryService.HistoryPage(List.of(),0,0,20,2));
        languageMvc.perform(get("/api/admin/menu-settings/2/html-histories").param("language","ko")
                        .header("X-Conference-Seq",1).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentRevisionNo").value(2));
        verify(translations).list(1L,2L,"ko",0,20);
        when(translations.list(1L,2L,"fr",0,20)).thenThrow(new IllegalArgumentException("지원하지 않는 언어"));
        languageMvc.perform(get("/api/admin/menu-settings/2/html-histories").param("language","fr")
                        .header("X-Conference-Seq",1).session(session)).andExpect(status().isBadRequest());
    }
    private final MenuSettingsService menus = mock(MenuSettingsService.class);
    private final MenuHtmlHistoryService histories = mock(MenuHtmlHistoryService.class);
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new MenuSettingsController(menus, histories))
                .addInterceptors(new AdminSessionInterceptor()).build();
        session = new MockHttpSession();
        session.setAttribute("adminSeq", 9L);
        session.setAttribute("adminRole", "admin");
    }

    @Test void requiresAdminSessionForReadAndRestore() throws Exception {
        mvc.perform(get("/api/admin/menu-settings/2/html-histories").header("X-Conference-Seq", 1)).andExpect(status().isUnauthorized());
        session.setAttribute("adminRole", "reviewer");
        mvc.perform(post("/api/admin/menu-settings/2/html-histories/5/restore").session(session).header("X-Conference-Seq", 1)).andExpect(status().isForbidden());
        verifyNoInteractions(menus, histories);
    }

    @Test void pagesWithinRequestedConferenceAndMenu() throws Exception {
        when(histories.list(1L, 2L, 0, 20)).thenReturn(new MenuHtmlHistoryService.HistoryPage(List.of(), 0, 0, 20, 1));
        mvc.perform(get("/api/admin/menu-settings/2/html-histories").session(session).header("X-Conference-Seq", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentRevisionNo").value(1));
    }

    @Test void restoreUsesActorFromSessionNotBody() throws Exception {
        when(menus.restoreHtml(1L, 2L, 5L, 9L, "Restore")).thenReturn(MenuSettingsResponse.builder().seq(2L).htmlRevisionNo(3L).build());
        mvc.perform(post("/api/admin/menu-settings/2/html-histories/5/restore").session(session).header("X-Conference-Seq", 1)
                .contentType("application/json").content("{\"changeMemo\":\"Restore\",\"createdBy\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.htmlRevisionNo").value(3));
        verify(menus).restoreHtml(1L, 2L, 5L, 9L, "Restore");
    }

    @Test void returnsMissingHistoryWithoutExposingOtherMenu() throws Exception {
        when(menus.restoreHtml(eq(1L), eq(2L), eq(5L), eq(9L), isNull()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "이력이 없습니다."));
        mvc.perform(post("/api/admin/menu-settings/2/html-histories/5/restore").session(session).header("X-Conference-Seq", 1))
                .andExpect(status().isNotFound());
    }
}
