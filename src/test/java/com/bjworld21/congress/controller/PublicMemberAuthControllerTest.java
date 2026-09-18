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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicMemberAuthControllerTest {
    private static final String DB_ENC_STRING = "test-secret";

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        PersonalDataProperties personalDataProperties = mock(PersonalDataProperties.class);
        ConferenceSettingsService conferenceSettingsService = mock(ConferenceSettingsService.class);
        when(personalDataProperties.requireDbEncString()).thenReturn(DB_ENC_STRING);
        when(conferenceSettingsService.getLatestConferenceSeq()).thenReturn(7L);

        mockMvc = MockMvcBuilders.standaloneSetup(new PublicMemberAuthController(
                memberRepository,
                passwordEncoder,
                personalDataProperties,
                conferenceSettingsService
        )).build();
    }

    @Test
    void logsInMemberWithIsolatedMemberSession() throws Exception {
        Member member = Member.builder().seq(11L).password("encoded-password").build();
        when(memberRepository.findByEmail(7L, "member@example.com", DB_ENC_STRING)).thenReturn(member);
        when(passwordEncoder.matches("Password123!", "encoded-password")).thenReturn(true);

        mockMvc.perform(post("/api/public/members/login")
                        .session(new MockHttpSession())
                        .param("email", " MEMBER@Example.com ")
                        .param("password", " Password123! "))
                .andExpect(status().isNoContent())
                .andExpect(request().sessionAttribute("memberSeq", 11L))
                .andExpect(request().sessionAttribute("memberConferenceSeq", 7L))
                .andExpect(request().sessionAttribute(
                        com.bjworld21.congress.security.MemberCredentialFingerprint.SESSION_ATTRIBUTE,
                        com.bjworld21.congress.security.MemberCredentialFingerprint.hash("encoded-password")));

        verify(memberRepository).findByEmail(7L, "member@example.com", DB_ENC_STRING);
    }

    @Test
    void rejectsInvalidCredentialsWithoutRevealingWhichValueFailed() throws Exception {
        mockMvc.perform(post("/api/public/members/login")
                        .param("email", "missing@example.com")
                        .param("password", "WrongPassword"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid email or password"));
    }

    @Test
    void expiresMemberSessionWhenThePublishedConferenceChanges() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("memberSeq", 11L);
        session.setAttribute("memberConferenceSeq", 6L);

        mockMvc.perform(get("/api/public/members/session").session(session))
                .andExpect(status().isUnauthorized());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logsOutMemberAndReturnsHome() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("memberSeq", 11L);

        mockMvc.perform(post("/api/public/members/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        assertThat(session.isInvalid()).isTrue();
    }

}
