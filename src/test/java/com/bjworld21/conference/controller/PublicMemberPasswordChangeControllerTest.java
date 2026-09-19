package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.SecurityConfig;
import com.bjworld21.conference.security.MemberCredentialFingerprint;
import com.bjworld21.conference.service.ConferenceSettingsService;
import com.bjworld21.conference.service.MemberPasswordChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({SecurityConfig.class, PublicMemberPasswordChangeControllerTest.WebSecurity.class})
class PublicMemberPasswordChangeControllerTest {
    @Configuration @EnableWebSecurity @EnableWebMvc
    static class WebSecurity {}
    @Autowired private FilterChainProxy filters;
    private MemberPasswordChangeService service;
    private MockMvc mvc;
    private MockHttpSession session;
    private static final String URL = "/api/public/7/members/password/change";
    private static final String BODY = """
            {"currentPassword":"OldPassword123!","newPassword":"NewPassword123!","newPasswordConfirm":"NewPassword123!",
             "memberSeq":999,"conferenceSeq":999}
            """;

    @BeforeEach
    void setUp() {
        service = mock(MemberPasswordChangeService.class);
        var conferences = mock(ConferenceSettingsService.class);
        when(conferences.getLatestConferenceSeq()).thenReturn(7L);
        mvc = MockMvcBuilders.standaloneSetup(new PublicMemberPasswordChangeController(service, conferences))
                .apply(springSecurity(filters)).addFilters(new com.bjworld21.conference.publicsite.PublicSiteTestContext()).build();
        session = new MockHttpSession();
        PublicMemberSession.signIn(session, 7, 11, "fingerprint");


        session.setAttribute("adminSeq", 3L);
    }

    @Test
    void successUsesSessionOwnershipAndClearsOnlyMemberAuthentication() throws Exception {
        mvc.perform(post(URL).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).change(7L, 11L, "fingerprint", "OldPassword123!", "NewPassword123!", "NewPassword123!");
        assertThat(PublicMemberSession.resolve(session, 7)).isNull();
        assertThat(session.getAttribute("memberConferenceSeq")).isNull();
        assertThat(PublicMemberSession.fingerprint(session, 7)).isNull();
        assertThat(session.getAttribute("adminSeq")).isEqualTo(3L);
    }

    @Test
    void rejectsAnonymousAndCrossConferenceSessions() throws Exception {
        mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        PublicMemberSession.signOut(session, 7); PublicMemberSession.signIn(session, 8, 11, "other");
        mvc.perform(post(URL).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        assertThat(session.getAttribute("adminSeq")).isEqualTo(3L);
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingCsrf() throws Exception {
        mvc.perform(post(URL).session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(header().string("X-CSRF-ERROR", "true"));
        verifyNoInteractions(service);
    }

    @Test
    void fieldErrorsRetainSessionAndRateLimitIncludesRetryHeader() throws Exception {
        doThrow(new MemberPasswordChangeService.InvalidPasswordException("currentPassword", "Incorrect password."))
                .when(service).change(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
        mvc.perform(post(URL).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("currentPassword"));
        assertThat(PublicMemberSession.resolve(session, 7).memberSeq()).isEqualTo(11L);
        doThrow(new MemberPasswordChangeService.TooManyAttemptsException())
                .when(service).change(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
        mvc.perform(post(URL).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "3600"));
    }

    @Test
    void staleCredentialClearsMemberSession() throws Exception {
        doThrow(new MemberPasswordChangeService.SessionExpiredException())
                .when(service).change(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
        mvc.perform(post(URL).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        assertThat(PublicMemberSession.resolve(session, 7)).isNull();
        assertThat(session.getAttribute("adminSeq")).isEqualTo(3L);
    }

    @Test
    void internalErrorsNeverExposeDetails() throws Exception {
        doThrow(new IllegalStateException("secret database details"))
                .when(service).change(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
        mvc.perform(post(URL).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Password change is temporarily unavailable. Please try again later."));
    }
}
