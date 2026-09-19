package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.*;
import com.bjworld21.conference.entity.AdminAccount;
import com.bjworld21.conference.entity.AdminAccessRequest;
import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAccessRequestMailApprovalControllerTest {
    private final AdminAccessRequestMailLink links = mock(AdminAccessRequestMailLink.class);
    private final AdminAccessRequestService service = mock(AdminAccessRequestService.class);
    private final AdminCredentialVerifier credentials = mock(AdminCredentialVerifier.class);
    private final AdminIpAccessCache cache = mock(AdminIpAccessCache.class);
    private MockMvc mvc;
    private static final String URL = "/api/access-requests/3/mail-approval";
    private static final String BODY = """
            {"email":"maintenance","password":"test-password","expires":123,"signature":"signed-link"}
            """;

    @BeforeEach void setUp() {
        var properties = new AdminIpAccessProperties(); properties.setEnabled(true);
        mvc = MockMvcBuilders.standaloneSetup(new AdminAccessRequestMailApprovalController(links, service, credentials))
                .addInterceptors(new AdminIpAccessInterceptor(properties, cache, new ClientIpResolver(properties)))
                .addMappedInterceptors(new String[]{"/api/admin/**"}, new AdminSessionInterceptor()).build();
        when(cache.isAllowed(any(InetAddress.class))).thenReturn(true);
    }

    @Test void openingLinkShowsRequestWithoutApprovingOrCreatingLoginSession() throws Exception {
        var request = new AdminAccessRequest(); request.setSeq(3L); request.setStatus("REQUESTED");
        when(service.findBySeq(3L)).thenReturn(request);
        mvc.perform(get(URL).param("expires", "123").param("signature", "signed-link"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.seq").value(3));
        verify(links).verify(3L, 123, "signed-link");
        verify(service, never()).approve(any(), any());
        verifyNoInteractions(credentials);
    }

    @Test void approvesWithAuthenticatedAdministratorIdWithoutExistingSession() throws Exception {
        when(credentials.verifyActiveAdministrator("maintenance", "test-password"))
                .thenReturn(AdminAccount.builder().seq(9L).build());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        var order = inOrder(links, credentials, service);
        order.verify(links).verify(3L, 123, "signed-link");
        order.verify(credentials).verifyActiveAdministrator("maintenance", "test-password");
        order.verify(service).approve(3L, 9L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"APPROVED", "REJECTED", "EXPIRED"})
    void completedRequestOnlyExposesStatus(String requestStatus) throws Exception {
        var request = new AdminAccessRequest();
        request.setSeq(3L); request.setStatus(requestStatus);
        request.setRequesterName("private-name"); request.setContact("010-1234-5678");
        request.setRequestIp("192.0.2.1"); request.setPurpose("private-purpose");
        when(service.findBySeq(3L)).thenReturn(request);
        mvc.perform(get(URL).param("expires", "123").param("signature", "signed-link"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"seq\":3,\"status\":\"" + requestStatus + "\"}", true));
    }

    @Test void invalidCredentialsCannotApprove() throws Exception {
        when(credentials.verifyActiveAdministrator(any(), any()))
                .thenThrow(new AdminCredentialVerifier.AuthenticationRejectedException("invalid"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void invalidLinkCannotReadOrApprove() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid link")).when(links).verify(any(), anyLong(), any());
        mvc.perform(get(URL).param("expires", "123").param("signature", "signed-link")).andExpect(status().isForbidden());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service, credentials);
    }

    @Test void ipRestrictionIsPreservedForReadAndApproval() throws Exception {
        when(cache.isAllowed(any(InetAddress.class))).thenReturn(false);
        mvc.perform(get(URL).param("expires", "123").param("signature", "signed-link")).andExpect(status().isForbidden());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(links, service, credentials);
    }

    @Test void alreadyRejectedOrExpiredRequestReturnsConflict() throws Exception {
        when(credentials.verifyActiveAdministrator(any(), any())).thenReturn(AdminAccount.builder().seq(9L).build());
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "already processed")).when(service).approve(3L, 9L);
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
    }
}
