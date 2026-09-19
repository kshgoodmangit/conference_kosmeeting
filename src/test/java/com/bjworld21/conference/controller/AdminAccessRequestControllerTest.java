package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.AdminIpAccessInterceptor;
import com.bjworld21.conference.config.AdminIpAccessProperties;
import com.bjworld21.conference.config.AdminSessionInterceptor;
import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.security.RequestSiteUrlResolver;
import com.bjworld21.conference.service.AdminAccessRequestService;
import com.bjworld21.conference.service.AdminIpAccessCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAccessRequestControllerTest {
    private final AdminAccessRequestService service = mock(AdminAccessRequestService.class);
    private final AdminIpAccessCache cache = mock(AdminIpAccessCache.class);
    private MockMvc mvc;
    private final String input = """
            {"affiliation":"소속","name":"이름","contact":"010-1234-5678","purpose":"업무",
             "startDate":"2026-09-16","endDate":"2026-09-17"}
            """;

    @BeforeEach void setUp() {
        var properties = new AdminIpAccessProperties();
        properties.setEnabled(true);
        var resolver = new ClientIpResolver(properties);
        mvc = MockMvcBuilders.standaloneSetup(new AdminAccessRequestController(service, resolver, new RequestSiteUrlResolver(resolver)))
                .addInterceptors(new AdminIpAccessInterceptor(properties, cache, resolver))
                .addMappedInterceptors(new String[]{"/api/admin/**"}, new AdminSessionInterceptor()).build();
        when(service.submit(any(), anyString(), anyString())).thenReturn(new AdminAccessRequestService.Submission(false, "접수 완료"));
    }

    @Test void publicInfoUsesRequestDomainAndIgnoresUntrustedForwardingHeaders() throws Exception {
        mvc.perform(get("/api/access-requests/info").header("Host", "conference.example:8443")
                        .header("X-Forwarded-Host", "evil.example")
                        .header("X-Forwarded-Proto", "http")
                        .header("X-Forwarded-For", "198.51.100.1")
                        .with(request -> { request.setRemoteAddr("203.0.113.10"); request.setScheme("https"); return request; }))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.domain").value("conference.example:8443"))
                .andExpect(jsonPath("$.clientIp").value("203.0.113.10"));
        verifyNoInteractions(cache);
    }

    @Test void disallowedIpCanSubmitButCannotApprove() throws Exception {
        mvc.perform(post("/api/access-requests").contentType(MediaType.APPLICATION_JSON).content(input)
                        .with(request -> { request.setRemoteAddr("203.0.113.10"); request.setScheme("https"); return request; }))
                .andExpect(status().isCreated());
        verify(service).submit(any(), eq("203.0.113.10"), eq("https://localhost:80"));
        mvc.perform(post("/api/admin/access-requests/1/approve").session(session("maintenance")))
                .andExpect(status().isForbidden());
        verify(service, never()).approve(anyLong(), anyLong());
    }

    @Test void invalidInputNeverReachesService() throws Exception {
        mvc.perform(post("/api/access-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).submit(any(), any(), any());
    }

    @Test void listAndProcessingRequireFullAdministratorOnAllowedIp() throws Exception {
        when(cache.isAllowed(any(InetAddress.class))).thenReturn(true);
        mvc.perform(get("/api/admin/access-requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/access-requests").session(session("reviewer"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/access-requests/1/approve").session(session("reviewer"))).andExpect(status().isForbidden());
        when(service.findPage("", "", 1, 20)).thenReturn(new AdminAccessRequestService.Page(List.of(), Map.of("total",0), 1,20));
        mvc.perform(get("/api/admin/access-requests").session(session("maintenance")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/admin/access-requests/1/approve").session(session("maintenance"))).andExpect(status().isOk());
        verify(service).approve(1L, 9L);
    }

    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        session.setAttribute("adminSeq", 9L); session.setAttribute("adminRole", role);
        return session;
    }
}
