package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AdminLoginRequest;
import com.bjworld21.conference.dto.AdminLoginResponse;
import com.bjworld21.conference.dto.AdminResponse;
import com.bjworld21.conference.service.AdminAccessLogService;
import com.bjworld21.conference.service.AdminBulkImportService;
import com.bjworld21.conference.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerLoginTest {

    @Mock
    private AdminService adminService;

    @Mock
    private AdminBulkImportService adminBulkImportService;

    @Mock
    private AdminAccessLogService adminAccessLogService;

    @InjectMocks
    private AdminController adminController;

    @Test
    void recordsAccessLogAfterSuccessfulLogin() {
        AdminLoginResponse loginResponse = loginResponse();
        when(adminService.login(any(AdminLoginRequest.class))).thenReturn(loginResponse);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        ResponseEntity<?> response = adminController.login("admin", "password", request, session);

        assertThat(response.getStatusCode())
                .withFailMessage("Unexpected login response: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(session.getAttribute("adminSeq")).isEqualTo(1L);
        assertThat(session.getAttribute("adminRole")).isEqualTo("admin");
        verify(adminAccessLogService).recordSuccessfulLogin(loginResponse, request);
    }

    @Test
    void storesReviewerConferenceInSessionAfterReviewerLogin() {
        AdminLoginResponse loginResponse = AdminLoginResponse.builder()
                .seq(42L)
                .email("reviewer@example.com")
                .adminName("Reviewer")
                .role("reviewer")
                .conferenceSeq(7L)
                .message("로그인 성공")
                .build();
        when(adminService.login(any(AdminLoginRequest.class))).thenReturn(loginResponse);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        ResponseEntity<?> response = adminController.login(
                "reviewer@example.com", "password", request, session
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(session.getAttribute("adminSeq")).isEqualTo(42L);
        assertThat(session.getAttribute("adminRole")).isEqualTo("reviewer");
        assertThat(session.getAttribute("reviewerConferenceSeq")).isEqualTo(7L);
    }

    @Test
    void doesNotRecordAccessLogWhenAuthenticationFails() {
        when(adminService.login(any(AdminLoginRequest.class)))
                .thenThrow(new IllegalArgumentException("비밀번호가 일치하지 않습니다."));
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = adminController.login(
                "admin", "wrong-password", request, new MockHttpSession());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(adminAccessLogService);
    }

    @Test
    void keepsLoginSuccessfulWhenAccessLogStorageFails() {
        AdminLoginResponse loginResponse = loginResponse();
        when(adminService.login(any(AdminLoginRequest.class))).thenReturn(loginResponse);
        doThrow(new IllegalStateException("database unavailable"))
                .when(adminAccessLogService)
                .recordSuccessfulLogin(any(AdminLoginResponse.class), any(MockHttpServletRequest.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        ResponseEntity<?> response = adminController.login(
                "admin", "password", request, session);

        assertThat(response.getStatusCode())
                .withFailMessage("Unexpected login response: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(loginResponse);
    }

    @Test
    void restoresAdminFromActiveServerSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminSeq", 1L);
        session.setAttribute("adminRole", "admin");
        request.setSession(session);
        when(adminService.getAdmin(1L, 1L)).thenReturn(AdminResponse.builder()
                .seq(1L)
                .email("admin")
                .adminName("Administrator")
                .role("admin")
                .status("active")
                .build());

        ResponseEntity<?> response = adminController.getSession(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(AdminLoginResponse.class);
        AdminLoginResponse body = (AdminLoginResponse) response.getBody();
        assertThat(body.getSeq()).isEqualTo(1L);
        assertThat(body.getEmail()).isEqualTo("admin");
        assertThat(body.getAdminName()).isEqualTo("Administrator");
        assertThat(body.getRole()).isEqualTo("admin");
    }

    @Test
    void restoresReviewerSessionWithCurrentReviewerConference() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminSeq", 42L);
        session.setAttribute("adminRole", "reviewer");
        session.setAttribute("reviewerConferenceSeq", 3L);
        request.setSession(session);
        when(adminService.requireCurrentReviewerConferenceSeq(42L)).thenReturn(7L);
        when(adminService.getAdmin(7L, 42L)).thenReturn(AdminResponse.builder()
                .seq(42L)
                .email("reviewer@example.com")
                .adminName("Reviewer")
                .role("reviewer")
                .status("active")
                .build());

        ResponseEntity<?> response = adminController.getSession(null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(session.getAttribute("reviewerConferenceSeq")).isEqualTo(7L);
        AdminLoginResponse body = (AdminLoginResponse) response.getBody();
        assertThat(body.getConferenceSeq()).isEqualTo(7L);
    }

    @Test
    void rejectsMissingServerSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = adminController.getSession(null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(adminService);
    }

    @Test
    void lockedLoginDoesNotCreateAuthenticatedSession() {
        when(adminService.login(any(AdminLoginRequest.class)))
                .thenThrow(new IllegalArgumentException("비밀번호 5회 실패로 계정이 잠겼습니다."));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        ResponseEntity<?> response = adminController.login("admin", "password", request, session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().toString()).contains("계정이 잠겼습니다");
        assertThat(session.getAttribute("adminSeq")).isNull();
        verifyNoInteractions(adminAccessLogService);
    }

    @Test
    void selfResetCannotUnlockAccountThroughExistingSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminSeq", 1L);
        request.getSession().setAttribute("adminRole", "admin");

        assertThat(adminController.resetPassword(1L, request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(adminService);
    }

    @Test
    void anotherAdministratorCanResetPassword() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminSeq", 2L);
        request.getSession().setAttribute("adminRole", "admin");
        when(adminService.resetPassword(1L)).thenReturn("temporary-password");

        ResponseEntity<?> response = adminController.resetPassword(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("temporary-password");
    }

    private AdminLoginResponse loginResponse() {
        return AdminLoginResponse.builder()
                .seq(1L)
                .email("admin")
                .adminName("Administrator")
                .role("admin")
                .message("로그인 성공")
                .build();
    }
}
