package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.AdminIpAccessProperties;
import com.bjworld21.congress.config.SecurityConfig;
import com.bjworld21.congress.security.ClientIpResolver;
import com.bjworld21.congress.service.MemberEmailVerificationService;
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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({SecurityConfig.class, PublicMemberEmailVerificationControllerTest.WebSecurity.class})
class PublicMemberEmailVerificationControllerTest {
    @Configuration @EnableWebSecurity @EnableWebMvc
    static class WebSecurity {}
    @Autowired private FilterChainProxy filters;
    private MemberEmailVerificationService service;
    private MockMvc mvc;
    private static final String URL = "/api/public/members/email-verification/send";

    @BeforeEach
    void setup() {
        service = mock(MemberEmailVerificationService.class);
        var controller = new PublicMemberEmailVerificationController(service, new ClientIpResolver(new AdminIpAccessProperties()));
        mvc = MockMvcBuilders.standaloneSetup(controller).apply(springSecurity(filters)).build();
    }

    @Test
    void successfulSendReturnsTimingButNoCodeAndUsesResolvedIp() throws Exception {
        when(service.sendCode(anyString(), anyString(), any()))
                .thenReturn(new MemberEmailVerificationService.SendResult("Code sent", 600, 60));
        mvc.perform(post(URL).with(csrf()).header("X-Forwarded-For", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60))
                .andExpect(jsonPath("$.code").doesNotExist());
        verify(service).sendCode(eq("member@example.com"), eq("127.0.0.1"), any());
    }

    @Test
    void requestsWithoutCsrfCannotSend() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void invalidAddressCannotSend() throws Exception {
        mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rateLimitReturnsRetryAdvice() throws Exception {
        when(service.sendCode(anyString(), anyString(), any())).thenThrow(new MemberEmailVerificationService.TooManyRequestsException());
        mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void smtpFailureDoesNotExposeSensitiveDetails() throws Exception {
        when(service.sendCode(anyString(), anyString(), any())).thenThrow(new IllegalStateException("smtp-secret"));
        mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Email verification is temporarily unavailable. Please try again later."));
    }

    @Test
    void verifiesCodeWithCsrfAndReturnsOwnershipResult() throws Exception {
        when(service.verifyCode(anyString(), anyString(), anyString(), any()))
                .thenReturn(new MemberEmailVerificationService.VerifyResult("Email verified", false, 1800));
        mvc.perform(post(URL.replace("/send", "/verify")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.existingMember").value(false))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800))
                .andExpect(jsonPath("$.code").doesNotExist());
        verify(service).verifyCode(eq("member@example.com"), eq("123456"), eq("127.0.0.1"), any());
    }

    @Test
    void verificationRequiresCsrf() throws Exception {
        mvc.perform(post(URL.replace("/send", "/verify"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void distinguishesRetryableWrongCodeFromExpiredChallenge() throws Exception {
        when(service.verifyCode(anyString(), anyString(), anyString(), any()))
                .thenThrow(new MemberEmailVerificationService.IncorrectCodeException("4 attempts remaining"))
                .thenThrow(new MemberEmailVerificationService.InvalidCodeException("Please request a new code"))
                .thenThrow(new MemberEmailVerificationService.VerificationRateLimitException());
        mvc.perform(post(URL.replace("/send", "/verify")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().string("4 attempts remaining"));
        mvc.perform(post(URL.replace("/send", "/verify")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isGone());
        mvc.perform(post(URL.replace("/send", "/verify")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "3600"));
    }
}
