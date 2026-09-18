package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.entity.Member;
import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.service.ConferenceSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicMemberAuthControllerTest {
    private static final String DB_ENC_STRING = "test-secret";
    private MemberRepository members;
    private PasswordEncoder encoder;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        members = mock(MemberRepository.class);
        encoder = mock(PasswordEncoder.class);
        var secrets = mock(PersonalDataProperties.class);
        when(secrets.requireDbEncString()).thenReturn(DB_ENC_STRING);
        mvc = MockMvcBuilders.standaloneSetup(new PublicMemberAuthController(members, encoder, secrets,
                mock(ConferenceSettingsService.class)))
                .addFilters(new com.bjworld21.congress.publicsite.PublicSiteTestContext()).build();
    }
    @Test void logsInMemberWithIsolatedMemberSession() throws Exception {
        when(members.findByEmail(7L, "member@example.com", DB_ENC_STRING))
                .thenReturn(Member.builder().seq(11L).password("encoded-password").build());
        when(encoder.matches("Password123!", "encoded-password")).thenReturn(true);
        var session = new MockHttpSession();
        PublicMemberSession.signIn(session, 8, 22, "other");
        mvc.perform(post("/api/public/7/members/login").session(session)
                        .param("email", " MEMBER@Example.com ").param("password", " Password123! "))
                .andExpect(status().isNoContent());
        assertThat(PublicMemberSession.resolve(session, 7).memberSeq()).isEqualTo(11);
        assertThat(PublicMemberSession.resolve(session, 8).memberSeq()).isEqualTo(22);
        assertThat(PublicMemberSession.fingerprint(session, 7)).isEqualTo(
                com.bjworld21.congress.security.MemberCredentialFingerprint.hash("encoded-password"));
        verify(members).findByEmail(7L, "member@example.com", DB_ENC_STRING);
    }
    @Test void rejectsInvalidCredentialsWithoutRevealingWhichValueFailed() throws Exception {
        mvc.perform(post("/api/public/7/members/login").param("email", "missing@example.com").param("password", "WrongPassword"))
                .andExpect(status().isUnauthorized()).andExpect(content().string("Invalid email or password"));
    }
    @Test void requestingAnotherConferenceDoesNotDestroyExistingLogin() throws Exception {
        var session = new MockHttpSession();
        PublicMemberSession.signIn(session, 6, 11, "fingerprint");
        mvc.perform(get("/api/public/7/members/session").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isFalse();
        assertThat(PublicMemberSession.resolve(session, 6).memberSeq()).isEqualTo(11);
        mvc.perform(get("/api/public/6/members/session").session(session)).andExpect(status().isNoContent());
    }
    @Test void logoutClearsOnlyTargetConferenceAndReturnsItsLanguageHome() throws Exception {
        var session = new MockHttpSession();
        PublicMemberSession.signIn(session, 7, 11, "one");
        PublicMemberSession.signIn(session, 8, 22, "two");
        session.setAttribute("adminSeq", 99L);
        session.setAttribute("csrf", "retain");
        mvc.perform(post("/api/public/7/members/logout").param("lang", "ko").session(session))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/apdrc8/ko/"));
        assertThat(PublicMemberSession.resolve(session, 7)).isNull();
        assertThat(PublicMemberSession.resolve(session, 8).memberSeq()).isEqualTo(22);
        assertThat(session.getAttribute("adminSeq")).isEqualTo(99L);
        assertThat(session.getAttribute("csrf")).isEqualTo("retain");
        assertThat(session.isInvalid()).isFalse();
    }
    @Test void legacyUnscopedLoginEndpointIsNotExposed() throws Exception {
        mvc.perform(post("/api/public/members/login").param("email", "member@example.com").param("password", "password"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(members);
    }
}
