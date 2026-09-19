package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.PublicPreRegistrationData;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.bjworld21.congress.service.PublicPreRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicPreRegistrationControllerTest {
    private PublicPreRegistrationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PublicPreRegistrationService.class);
        ConferenceSettingsService conferences = mock(ConferenceSettingsService.class);
        when(conferences.getLatestConferenceSeq()).thenReturn(1L);
        when(service.create(any(), any(), any())).thenReturn(new PublicPreRegistrationData.Registration(
                42L, "PR-2026-000042", 3L, "Member", "EARLY_BIRD", "USD",
                new BigDecimal("120.00"), BigDecimal.ZERO, new BigDecimal("120.00"), List.of(), "SUBMITTED", "UNPAID"
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PublicPreRegistrationController(service, conferences)
        ).addFilters(new com.bjworld21.congress.publicsite.PublicSiteTestContext())
                .setControllerAdvice(new com.bjworld21.congress.publicsite.PublicApiAdvice(
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void initialFormDoesNotInventASuccessMessage() throws Exception {
        when(service.form(1L, 7L)).thenReturn(new PublicPreRegistrationData.Form(
                null, "EARLY_BIRD", "Early Bird", null, null, "KRW", List.of(), List.of(),
                null, true, true, ""));

        mockMvc.perform(get("/api/public/1/pre-registrations/form")
                        .sessionAttr("publicMember.1", new PublicMemberSession(7L, 1L)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value(""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.registrationOpen").value(true));
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/public/1/pre-registrations/form"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/public/1/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/public/1/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/public/1/pre-registrations/cancel"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).form(any(), any());
        verify(service, never()).create(any(), any(), any());
        verify(service, never()).update(any(), any(), any());
        verify(service, never()).cancel(any(), any());
    }

    @Test
    void createsForTheLoggedInMember() throws Exception {
        mockMvc.perform(post("/api/public/1/pre-registrations")
                        .sessionAttr("publicMember.1", new PublicMemberSession(7L, 1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorySeq\":3,\"privacyAgreed\":true,\"termsAgreed\":true}"))
                .andExpect(status().isCreated());

        verify(service).create(any(), any(), any(PublicPreRegistrationData.Request.class));
    }

    @Test
    void loadsTheFormForTheLoggedInMember() throws Exception {
        mockMvc.perform(get("/api/public/1/pre-registrations/form")
                        .sessionAttr("publicMember.1", new PublicMemberSession(7L, 1L)))
                .andExpect(status().isOk());

        verify(service).form(1L, 7L);
    }

    @Test
    void updatesForTheLoggedInMember() throws Exception {
        mockMvc.perform(put("/api/public/1/pre-registrations")
                        .sessionAttr("publicMember.1", new PublicMemberSession(7L, 1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorySeq\":3,\"options\":[]}"))
                .andExpect(status().isOk());

        verify(service).update(any(), any(), any(PublicPreRegistrationData.Request.class));
    }

    @Test
    void cancelsForTheLoggedInMember() throws Exception {
        mockMvc.perform(post("/api/public/1/pre-registrations/cancel")
                        .sessionAttr("publicMember.1", new PublicMemberSession(7L, 1L)))
                .andExpect(status().isNoContent());

        verify(service).cancel(1L, 7L);
    }

    @Test
    void rendersThePublishedOnlineRegistrationForm() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var messages = new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("public-ui");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        engine.setTemplateEngineMessageSource(messages);
        var site = new com.bjworld21.congress.publicsite.PublicSiteContext(1L, "apdrc8", "en", java.util.List.of("en", "ko"), "/apdrc8/en", "/api/public/1");

        Context context = new Context(java.util.Locale.ENGLISH);
        context.setVariable("siteContext", site);
        String html = engine.process("public/pages/online-registration", context);

        assertThat(html).contains(
                "id=\"public-registration-form\"",
                "/api/public/1/pre-registrations",
                "id=\"registration-category\"",
                "data-registration-categories",
                "name=\"paymentMethod\"",
                "type=\"radio\"",
                "data-registration-options",
                "data-registration-member=\"name\"",
                "data-registration-member=\"email\"",
                "name=\"privacyAgreed\"",
                "data-cancel-registration",
                "data-registration-cancel-dialog"
        );
        assertThat(html).doesNotContain("Registration Period", "data-category-description");

    }
}
