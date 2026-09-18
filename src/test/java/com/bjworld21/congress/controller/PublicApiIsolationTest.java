package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.PublicApiConfig;
import com.bjworld21.congress.publicsite.PublicApiAdvice;
import com.bjworld21.congress.publicsite.PublicSiteService;
import com.bjworld21.congress.publicsite.PublicSiteTestContext;
import com.bjworld21.congress.service.AbstractPresentationAttachmentService;
import com.bjworld21.congress.service.AbstractSubmissionService;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicApiIsolationTest {
    private final PublicSiteService sites = mock(PublicSiteService.class);
    private final AbstractSubmissionService abstracts = mock(AbstractSubmissionService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        when(sites.resolveApi(7, "ko")).thenReturn(PublicSiteTestContext.context(7, "ko"));
        when(sites.resolveApi(7, "en")).thenReturn(PublicSiteTestContext.context(7, "en"));
        when(sites.resolveApi(8, "en")).thenReturn(PublicSiteTestContext.context(8, "en"));
        mvc = MockMvcBuilders.standaloneSetup(new PublicAbstractController(abstracts,
                        mock(AbstractPresentationAttachmentService.class), mock(ConferenceSettingsService.class)))
                .addInterceptors(new PublicApiConfig(sites))
                .setControllerAdvice(new PublicApiAdvice(new ObjectMapper())).build();
    }

    @Test void localizedErrorsKeepStableCode() throws Exception {
        mvc.perform(get("/api/public/7/abstracts?lang=ko"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(header().string("Content-Language", "ko"));
        mvc.perform(get("/api/public/7/abstracts?lang=en"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Login is required."));
    }

    @Test void conferenceAndMemberAreAlwaysTakenFromScopedRequestAndLogin() throws Exception {
        var session = new MockHttpSession();
        PublicMemberSession.signIn(session, 7, 11, "a");
        PublicMemberSession.signIn(session, 8, 22, "b");
        mvc.perform(get("/api/public/7/abstracts/31?lang=en&conferenceSeq=8&memberSeq=22").session(session))
                .andExpect(status().isOk());
        verify(abstracts).getByMember(7L, 31L, 11L);
        mvc.perform(get("/api/public/8/abstracts/31?lang=en").session(session)).andExpect(status().isOk());
        verify(abstracts).getByMember(8L, 31L, 22L);
    }

    @Test void anotherConferenceCannotBorrowAuthenticationOrClearExistingSession() throws Exception {
        var session = new MockHttpSession();
        PublicMemberSession.signIn(session, 7, 11, "a");
        mvc.perform(get("/api/public/8/abstracts/31?lang=en").session(session)).andExpect(status().isUnauthorized());
        assertThat(PublicMemberSession.resolve(session, 7).memberSeq()).isEqualTo(11);
        verifyNoInteractions(abstracts);
    }

    @Test void missingOrForbiddenConferenceAndUnsupportedLanguageNeverReachServices() throws Exception {
        when(sites.resolveApi(999, "ko")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(sites.resolveApi(7, "ja")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/public/999/abstracts?lang=ko"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/public/7/abstracts?lang=ja"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verifyNoInteractions(abstracts);
    }

    @Test void koreanServiceErrorsTranslateForEnglishApiWithoutLeakingOtherConferenceData() throws Exception {
        var session = new MockHttpSession();
        PublicMemberSession.signIn(session, 7, 11, "a");
        when(abstracts.getByMember(7L, 31L, 11L)).thenThrow(new IllegalArgumentException("조회할 수 있는 회원 초록을 찾을 수 없습니다."));
        mvc.perform(get("/api/public/7/abstracts/31?lang=en").session(session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ABSTRACT_OWNER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Your abstract could not be found."));
    }
}
