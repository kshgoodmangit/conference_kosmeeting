package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.AdminSessionInterceptor;
import com.bjworld21.conference.service.SmsCampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SmsCampaignControllerTest {
    private MockMvc mvc;
    private SmsCampaignService service;
    @BeforeEach void setup() {
        service = mock(SmsCampaignService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SmsCampaignController(service))
                .addInterceptors(new AdminSessionInterceptor()).setControllerAdvice(new MailApiExceptionHandler()).build();
    }
    @Test void requiresAdminSession() throws Exception {
        mvc.perform(get("/api/admin/sms/campaigns")).andExpect(status().isUnauthorized());
        MockHttpSession reviewer = session(); reviewer.setAttribute("adminRole", "reviewer");
        mvc.perform(get("/api/admin/sms/campaigns").session(reviewer)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void validatesMessageAndInheritedRecipientGroups() throws Exception {
        mvc.perform(post("/api/admin/sms/campaigns").session(session()).contentType("application/json")
                .content("{\"title\":\"Notice\",\"senderNumber\":\"0212345678\",\"message\":\"hello\",\"recipientGroups\":[\"INVALID\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/sms/campaigns").session(session()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void noActualSendEndpointExists() throws Exception {
        mvc.perform(post("/api/admin/sms/campaigns/1/send").session(session())).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
    private MockHttpSession session() { MockHttpSession session = new MockHttpSession(); session.setAttribute("adminSeq", 1L); session.setAttribute("adminRole", "admin"); return session; }
}
