package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.AdminSessionInterceptor;
import com.bjworld21.congress.dto.PreRegistrationCreateRequest;
import com.bjworld21.congress.dto.PreRegistrationPageResponse;
import com.bjworld21.congress.dto.PreRegistrationResponse;
import com.bjworld21.congress.dto.PreRegistrationUpdateRequest;
import com.bjworld21.congress.service.ExcelDownloadAuditService;
import com.bjworld21.congress.service.PreRegistrationAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PreRegistrationAdminControllerTest {
    private PreRegistrationAdminService service;
    private MockMvc mvc;
    private MockHttpSession session;
    private static final String BODY = """
            {"memberSeq":7,"categorySeq":3,"periodType":"REGULAR",
             "currency":"USD","feeAmount":450.00,"adminMemo":"관리자 접수"}
            """;

    @BeforeEach
    void setUp() {
        service = mock(PreRegistrationAdminService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PreRegistrationAdminController(service, mock(ExcelDownloadAuditService.class)))
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8), new MappingJackson2HttpMessageConverter())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .addMappedInterceptors(new String[]{"/api/admin/**"}, new AdminSessionInterceptor()).build();
        session = new MockHttpSession();
        session.setAttribute("adminSeq", 1L);
        session.setAttribute("adminRole", "admin");
    }

    @Test
    void createRequiresAdministrator() throws Exception {
        mvc.perform(post("/api/admin/pre-registrations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        session.setAttribute("adminRole", "reviewer");
        mvc.perform(post("/api/admin/pre-registrations").session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void createBindsMemberAndCommonFormFields() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(PreRegistrationResponse.builder().seq(42L)
                .registrationNumber("PR-2026-000042").paymentStatus("UNPAID").build());
        mvc.perform(post("/api/admin/pre-registrations").header("X-Conference-Seq", "1").session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationNumber").value("PR-2026-000042"));
        ArgumentCaptor<PreRegistrationCreateRequest> request = ArgumentCaptor.forClass(PreRegistrationCreateRequest.class);
        verify(service).create(eq(1L), request.capture());
        assertThat(request.getValue().getMemberSeq()).isEqualTo(7L);
        assertThat(request.getValue().getCategorySeq()).isEqualTo(3L);
        assertThat(request.getValue().getFeeAmount()).isEqualByComparingTo("450.00");
    }

    @Test
    void createReturnsPlainTextValidationAndConflictErrors() throws Exception {
        when(service.create(eq(1L), any())).thenThrow(new IllegalArgumentException("신청 회원을 선택해 주세요."));
        mvc.perform(post("/api/admin/pre-registrations").header("X-Conference-Seq", "1").session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(content().string("신청 회원을 선택해 주세요."));
        doThrow(new IllegalStateException("이미 신청 완료된 사전등록 내역이 있는 회원입니다.")).when(service).create(eq(1L), any());
        mvc.perform(post("/api/admin/pre-registrations").header("X-Conference-Seq", "1").session(session).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void updatePaymentStatusRequiresAdministratorAndBindsStatus() throws Exception {
        String body = """
                {"categorySeq":3,"periodType":"REGULAR","currency":"USD","feeAmount":450,
                 "applicationStatus":"SUBMITTED","paymentStatus":"PAID"}
                """;
        mvc.perform(put("/api/admin/pre-registrations/42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        session.setAttribute("adminRole", "reviewer");
        mvc.perform(put("/api/admin/pre-registrations/42").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        session.setAttribute("adminRole", "admin");
        when(service.update(eq(1L), eq(42L), any())).thenReturn(PreRegistrationResponse.builder().seq(42L).paymentStatus("PAID").build());
        mvc.perform(put("/api/admin/pre-registrations/42").header("X-Conference-Seq", "1").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        ArgumentCaptor<PreRegistrationUpdateRequest> request = ArgumentCaptor.forClass(PreRegistrationUpdateRequest.class);
        verify(service).update(eq(1L), eq(42L), request.capture());
        assertThat(request.getValue().getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void listBindsOptionFilter() throws Exception {
        when(service.findPage(1L, 1, 20, "", null, 10L, "", "", "", null, null))
                .thenReturn(PreRegistrationPageResponse.builder().items(java.util.List.of()).build());

        mvc.perform(get("/api/admin/pre-registrations").header("X-Conference-Seq", "1").session(session).param("optionSeq", "10"))
                .andExpect(status().isOk());

        verify(service).findPage(1L, 1, 20, "", null, 10L, "", "", "", null, null);
    }
}
