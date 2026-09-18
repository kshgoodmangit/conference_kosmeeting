package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.AdminSessionInterceptor;
import com.bjworld21.congress.dto.SpeakerRequest;
import com.bjworld21.congress.service.SpeakerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpeakerControllerTest {
    SpeakerService service;
    MockMvc mvc;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        service = mock(SpeakerService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SpeakerController(service))
                .addMappedInterceptors(new String[]{"/api/admin/**"}, new AdminSessionInterceptor()).build();
        session = new MockHttpSession();
        session.setAttribute("adminSeq", 1L);
        session.setAttribute("adminRole", "admin");
    }

    @Test
    void rejectsAnonymousAndReviewerIncludingImageAccess() throws Exception {
        mvc.perform(get("/api/admin/speakers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/speakers/1/image")).andExpect(status().isUnauthorized());
        session.setAttribute("adminRole", "reviewer");
        mvc.perform(get("/api/admin/speakers").session(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/speakers/1").session(session)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void validatesRequiredFieldsLengthsAndNegativeOrder() throws Exception {
        mvc.perform(multipart("/api/admin/speakers").session(session)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/speakers").session(session)
                .param("speakerTypeCode", "5").param("displayName", " ").param("affiliation", "University"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/speakers").session(session)
                .param("speakerTypeCode", "5").param("displayName", "x".repeat(201)).param("affiliation", "University"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/speakers").session(session)
                .param("speakerTypeCode", "5").param("displayName", "Alice").param("affiliation", "University").param("sortOrder", "-1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void validatesEmailAndNumericBinding() throws Exception {
        mvc.perform(multipart("/api/admin/speakers").session(session)
                .param("speakerTypeCode", "5").param("displayName", "Alice").param("affiliation", "University").param("contactEmail", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/speakers").session(session)
                .param("speakerTypeCode", "invalid").param("displayName", "Alice").param("affiliation", "University"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void multipartPutBindsFlagsAndOptionalImageRemoval() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/speakers/1").header("X-Conference-Seq", "1").session(session)
                .param("speakerTypeCode", "5").param("displayName", "Alice").param("affiliation", "University")
                .param("featured", "true").param("enabled", "false").param("removeProfileImage", "true"))
                .andExpect(status().isOk());
        ArgumentCaptor<SpeakerRequest> request = ArgumentCaptor.forClass(SpeakerRequest.class);
        verify(service).update(eq(1L), eq(1L), request.capture(), isNull());
        assertThat(request.getValue().getFeatured()).isTrue();
        assertThat(request.getValue().getEnabled()).isFalse();
        assertThat(request.getValue().isRemoveProfileImage()).isTrue();
    }

    @Test
    void createsWithoutImageAndReturnsCreated() throws Exception {
        mvc.perform(multipart("/api/admin/speakers").header("X-Conference-Seq", "1").session(session)
                .param("speakerTypeCode", "5").param("displayName", "Alice").param("affiliation", "University"))
                .andExpect(status().isCreated());
        verify(service).create(eq(1L), any(), isNull());
    }
}
