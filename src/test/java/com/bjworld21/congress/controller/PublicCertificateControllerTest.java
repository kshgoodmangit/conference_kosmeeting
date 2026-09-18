package com.bjworld21.congress.controller;

import com.bjworld21.congress.service.ConferenceSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicCertificateControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ConferenceSettingsService conferences = mock(ConferenceSettingsService.class);
        when(conferences.getLatestConferenceSeq()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicCertificateController(conferences)).addFilters(new com.bjworld21.congress.publicsite.PublicSiteTestContext()).build();
    }

    @Test
    void downloadsCertificateOnlyForTheCurrentConferenceMember() throws Exception {
        mockMvc.perform(get("/api/public/7/members/certificate"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/public/7/members/certificate")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().string("Content-Disposition", containsString("certificate.png")));
    }
}
