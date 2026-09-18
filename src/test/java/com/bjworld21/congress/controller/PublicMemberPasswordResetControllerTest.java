package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.AdminIpAccessProperties;
import com.bjworld21.congress.config.SecurityConfig;
import com.bjworld21.congress.security.ClientIpResolver;
import com.bjworld21.congress.service.MemberPasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({SecurityConfig.class, PublicMemberPasswordResetControllerTest.WebSecurity.class})
class PublicMemberPasswordResetControllerTest {
    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class WebSecurity {}

    @Autowired private FilterChainProxy filters;
    private MemberPasswordResetService service;
    private MockMvc mvc;
    private static final String BASE = "/api/public/7/members/password-reset";
    private static final String TOKEN = "a".repeat(43);

    @BeforeEach
    void setUp() {
        service = mock(MemberPasswordResetService.class);
        var controller = new PublicMemberPasswordResetController(service, new ClientIpResolver(new AdminIpAccessProperties()));
        mvc = MockMvcBuilders.standaloneSetup(controller).apply(springSecurity(filters)).addFilters(new com.bjworld21.congress.publicsite.PublicSiteTestContext()).build();
    }

    @Test
    void anonymousRequestWithCsrfGetsNeutralAcceptedMessage() throws Exception {
        mvc.perform(post(BASE + "/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("If an account matches")));
        verify(service).requestReset("member@example.com", "127.0.0.1");
    }

    @Test
    void everyMutatingEndpointRejectsMissingCsrf() throws Exception {
        for (String endpoint : new String[]{"request", "validate", "confirm"}) {
            mvc.perform(post(BASE + "/" + endpoint).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden()).andExpect(header().string("X-CSRF-ERROR", "true"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void invalidEmailAndTokenAreRejectedBeforeServiceCalls() throws Exception {
        mvc.perform(post(BASE + "/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void validTokenAndMatchingPasswordReachConfirmationWithoutLogin() throws Exception {
        mvc.perform(post(BASE + "/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"password\":\"NewPassword123!\",\"passwordConfirm\":\"NewPassword123!\"}"))
                .andExpect(status().isNoContent());
        verify(service).resetPassword(TOKEN, "NewPassword123!", "NewPassword123!", "127.0.0.1");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {7, 17})
    void invalidPasswordLengthsAreRejectedBeforeServiceCalls(int length) throws Exception {
        String password = "a".repeat(length);
        mvc.perform(post(BASE + "/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"password\":\"" + password
                                + "\",\"passwordConfirm\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void expiredAndThrottledLinksReturnActionableMessages() throws Exception {
        doThrow(new MemberPasswordResetService.InvalidTokenException()).when(service).validateToken(anyString(), anyString());
        mvc.perform(post(BASE + "/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("expired")));
        doThrow(new MemberPasswordResetService.TooManyAttemptsException()).when(service).validateToken(anyString(), anyString());
        mvc.perform(post(BASE + "/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "3600"));
    }

    @Test
    void internalFailuresDoNotExposeDatabaseOrSmtpDetails() throws Exception {
        doThrow(new IllegalStateException("private database detail")).when(service).requestReset(anyString(), anyString());
        mvc.perform(post(BASE + "/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Password recovery is temporarily unavailable. Please try again later."));
    }

    @Test
    void forwardedIpIsIgnoredFromUntrustedCaller() throws Exception {
        mvc.perform(post(BASE + "/request").with(csrf()).header("X-Forwarded-For", "203.0.113.5")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(service).requestReset("member@example.com", "127.0.0.1");
    }
}
