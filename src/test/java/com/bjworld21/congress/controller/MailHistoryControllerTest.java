package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.AdminSessionInterceptor;
import com.bjworld21.congress.dto.MailHistoryData.*;
import com.bjworld21.congress.service.MailCampaignService;
import com.bjworld21.congress.service.MailHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MailHistoryControllerTest {
    private MailHistoryService service;
    private MailCampaignService campaigns;
    private MockMvc mvc;
    private MockHttpSession session;
    @BeforeEach void setup() {
        service = mock(MailHistoryService.class); campaigns = mock(MailCampaignService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MailHistoryController(service, campaigns))
                .setControllerAdvice(new MailApiExceptionHandler())
                .addMappedInterceptors(new String[]{"/api/admin/**"}, new AdminSessionInterceptor()).build();
        session = new MockHttpSession(); session.setAttribute("adminSeq", 7L); session.setAttribute("adminRole", "admin");
    }
    @Test void requiresAdminSessionForSaveListDetailAndAttachment() throws Exception {
        mvc.perform(get("/api/admin/mail-history").header("X-Conference-Seq", "1")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/mail-history").file(request("[1]")).header("X-Conference-Seq", "1")).andExpect(status().isUnauthorized());
        session.setAttribute("adminRole", "reviewer");
        for (String path : List.of("", "/1", "/1/attachments/2", "/emails", "/emails/41")) mvc.perform(get("/api/admin/mail-history" + path).header("X-Conference-Seq", "1").session(session)).andExpect(status().isForbidden());
        verifyNoInteractions(service, campaigns);
    }
    @Test void multipartSaveUsesConferenceHeaderAndSessionAdministrator() throws Exception {
        when(service.save(eq(1L), eq(7L), any(), any())).thenReturn(new Saved(10L, 1, 0, 0, 0));
        mvc.perform(multipart("/api/admin/mail-history").file(request("[1]")).header("X-Conference-Seq", "1").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seq").value(10));
        verify(service).save(eq(1L), eq(7L), argThat(request -> request.getSourceMenu().equals("abstracts")), isNull());
    }
    @Test void rejectsEmptyRecipientsMissingConferenceAndForeignAttachment() throws Exception {
        mvc.perform(multipart("/api/admin/mail-history").file(request("[]")).header("X-Conference-Seq", "1").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/mail-history").session(session)).andExpect(status().isBadRequest());
        when(service.requireHistory(2L, 10L)).thenThrow(new IllegalArgumentException("메일 이력을 찾을 수 없습니다."));
        mvc.perform(get("/api/admin/mail-history/10/attachments/11").header("X-Conference-Seq", "2").session(session)).andExpect(status().isBadRequest());
        verifyNoInteractions(campaigns);
    }
    @Test void emailEndpointsBindFiltersAndAlwaysUseConferenceHeader() throws Exception {
        EmailSummary summary = new EmailSummary(); summary.setTotalCount(1); summary.setHistoryCount(3);
        when(service.emails(any(), anyInt(), anyInt())).thenReturn(new EmailPage(List.of(), 1, 1, summary));
        when(service.emailHistories(any(), eq(41L), anyInt(), anyInt())).thenReturn(new EmailHistoryPage("person+tag@example.org", List.of(), 1, 1, summary));
        mvc.perform(get("/api/admin/mail-history/emails").session(session).header("X-Conference-Seq", "2")
                .param("conferenceSeq", "999").param("sourceMenu", "abstracts").param("status", "EXCLUDED").param("dateFrom", "2026-09-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.historyCount").value(3));
        verify(service).emails(argThat(f -> f.getConferenceSeq().equals(2L) && f.getSourceMenu().equals("abstracts") && f.getStatus().equals("EXCLUDED") && f.getDateFrom().toString().equals("2026-09-01")), eq(1), eq(20));
        mvc.perform(get("/api/admin/mail-history/emails/41").session(session).header("X-Conference-Seq", "2").param("keyword", "person+tag"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("person+tag@example.org"));
        verify(service).emailHistories(argThat(f -> f.getConferenceSeq().equals(2L) && f.getKeyword().equals("person+tag")), eq(41L), eq(1), eq(20));
        mvc.perform(get("/api/admin/mail-history/emails").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/mail-history/emails/41").header("X-Conference-Seq", "2")).andExpect(status().isUnauthorized());
    }

    private MockMultipartFile request(String ids) {
        return new MockMultipartFile("request", "request.json", "application/json", ("""
            {"requestKey":"223a3c98-5924-43d0-ad59-81d5ae0c40ac","sourceMenu":"abstracts",
             "subject":"안내","htmlContent":"<p>내용</p>","sourceSeqs":%s}
            """).formatted(ids).getBytes(StandardCharsets.UTF_8));
    }
}
