package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AbstractSubmissionRequest;
import com.bjworld21.conference.dto.AbstractSubmissionAttachmentResponse;
import com.bjworld21.conference.dto.AbstractSubmissionResponse;
import com.bjworld21.conference.dto.MemberListResponse;
import com.bjworld21.conference.service.AbstractPresentationAttachmentService;
import com.bjworld21.conference.service.AbstractSubmissionService;
import com.bjworld21.conference.service.ConferenceSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicAbstractControllerTest {
    private AbstractSubmissionService abstractSubmissionService;
    private AbstractPresentationAttachmentService abstractPresentationAttachmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        abstractSubmissionService = mock(AbstractSubmissionService.class);
        abstractPresentationAttachmentService = mock(AbstractPresentationAttachmentService.class);
        ConferenceSettingsService conferenceSettingsService = mock(ConferenceSettingsService.class);
        when(conferenceSettingsService.getLatestConferenceSeq()).thenReturn(7L);
        when(abstractSubmissionService.createByMember(any(), any(), any()))
                .thenReturn(AbstractSubmissionResponse.builder().seq(31L).build());
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PublicAbstractController(
                        abstractSubmissionService,
                        abstractPresentationAttachmentService,
                        conferenceSettingsService
                )
        ).addFilters(new com.bjworld21.conference.publicsite.PublicSiteTestContext()).build();
    }

    @Test
    void createsAnAbstractForTheLoggedInMember() throws Exception {
        mockMvc.perform(post("/api/public/7/abstracts")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"A title\",\"status\":\"submitted\"}"))
                .andExpect(status().isCreated());

        verify(abstractSubmissionService).createByMember(eq(7L), any(AbstractSubmissionRequest.class), eq(11L));
    }

    @Test
    void rejectsSubmissionWithoutMemberSession() throws Exception {
        mockMvc.perform(post("/api/public/7/abstracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Login is required."));

        verify(abstractSubmissionService, never()).createByMember(any(), any(), any());
    }

    @Test
    void loadsAndUpdatesOnlyThroughTheLoggedInMemberApi() throws Exception {
        when(abstractSubmissionService.getByMember(7L, 31L, 11L))
                .thenReturn(AbstractSubmissionResponse.builder().seq(31L).status("draft").build());
        when(abstractSubmissionService.updateByMember(eq(7L), eq(31L), eq(11L), any(AbstractSubmissionRequest.class)))
                .thenReturn(AbstractSubmissionResponse.builder().seq(31L).status("submitted").build());

        mockMvc.perform(get("/api/public/7/abstracts/31")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/public/7/abstracts/31")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"submitted\"}"))
                .andExpect(status().isOk());

        verify(abstractSubmissionService).getByMember(7L, 31L, 11L);
        verify(abstractSubmissionService).updateByMember(eq(7L), eq(31L), eq(11L), any(AbstractSubmissionRequest.class));
    }

    @Test
    void deletesTheLoggedInMembersDraft() throws Exception {
        mockMvc.perform(delete("/api/public/7/abstracts/31")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L)))
                .andExpect(status().isNoContent());

        verify(abstractSubmissionService).deleteDraftByMember(7L, 31L, 11L);
    }

    @Test
    void rejectsAbstractContentOverThreeHundredWords() throws Exception {
        String body = ("word ".repeat(301)).trim();
        mockMvc.perform(post("/api/public/7/abstracts")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveText\":\"" + body + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Abstract content must be 300 words or less."));

        verify(abstractSubmissionService, never()).createByMember(any(), any(), any());
    }

    @Test
    void reportsClosedSubmissionPeriodAsConflict() throws Exception {
        when(abstractSubmissionService.createByMember(any(), any(), any()))
                .thenThrow(new IllegalStateException("Abstract submission is currently closed."));

        mockMvc.perform(post("/api/public/7/abstracts")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"A title\",\"status\":\"submitted\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Abstract submission is currently closed."));
    }

    @Test
    void uploadsPresentationMaterialForTheLoggedInMember() throws Exception {
        when(abstractPresentationAttachmentService.addByMember(eq(7L), eq(31L), eq(11L), any()))
                .thenReturn(AbstractSubmissionAttachmentResponse.builder().seq(41L).abstractSeq(31L).build());

        mockMvc.perform(multipart("/api/public/7/abstracts/31/attachments")
                        .file("file", new byte[]{1})
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L)))
                .andExpect(status().isCreated());

        verify(abstractPresentationAttachmentService).addByMember(eq(7L), eq(31L), eq(11L), any());
    }

    @Test
    void rendersThePublicSubmissionForm() {
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
        var site = new com.bjworld21.conference.publicsite.PublicSiteContext(7L, "apdrc8", "en", java.util.List.of("en", "ko"), "/apdrc8/en", "/api/public/7");

        Context context = new Context(java.util.Locale.ENGLISH);
        context.setVariable("siteContext", site);
        context.setVariable("mypageMember", MemberListResponse.builder().firstName("Jane").lastName("Doe").build());
        context.setVariable("mypageAbstractDday", "D-30");
        context.setVariable("mypageRegistrationLabel", "Early-bird");
        context.setVariable("mypageRegistrationDday", "D-15");
        context.setVariable("mypageAbstracts", List.of());

        assertThat(engine.process("public/member/mypage", context)).contains("Early-bird", "D-15");
        assertThat(engine.process("public/pages/abstract-submission", context))
                .contains("id=\"public-abstract-form\"", "/api/public/7/abstracts", "data-institution-list", "data-author-list");
        assertThat(engine.process("public/member/mypage-abstract-review", context))
                .contains("id=\"public-abstract-review\"", "data-review-authors", "data-review-field=\"plagiarismPolicyConfirmed\"",
                        "data-presentation-upload-form", "data-presentation-attachments", "data-delete-abstract",
                        "data-abstract-delete-dialog", "data-confirm-abstract-delete", "Position");
        assertThat(engine.process("public/member/mypage-abstract-write", context))
                .contains("id=\"public-abstract-form\"", "id=\"abstract-institution-template\"", "id=\"abstract-author-template\"");
        assertThat(engine.process("public/member/mypage-abstract", context))
                .contains("My Abstracts", "/apdrc8/en/abstract-write")
                .doesNotContain("data-delete-abstract", "data-abstract-delete-dialog");
        assertThat(engine.process("public/member/mypage-certificate", context))
                .contains("href=\"/api/public/7/members/certificate?lang=en\"");
    }
}
