package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.AdminPasswordChangeRequest;
import com.bjworld21.congress.config.AdminAccountSessionInterceptor;
import com.bjworld21.congress.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSelfControllerTest {

    @Test
    void passwordChangeUsesLoggedInAdminAndInvalidatesSession() {
        AdminService adminService = mock(AdminService.class);
        AdminSelfController controller = new AdminSelfController(adminService);
        AdminPasswordChangeRequest request = AdminPasswordChangeRequest.builder()
                .currentPassword("current-password")
                .newPassword("new-password")
                .newPasswordConfirm("new-password")
                .build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminSeq", 73L);

        var result = controller.changeOwnPassword(request, session);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(session.isInvalid()).isTrue();
        verify(adminService).changeOwnPassword(73L, request);
    }

    @Test
    void reviewerCanChangePasswordThroughSharedAccountRoute() throws Exception {
        AdminService adminService = mock(AdminService.class);
        AdminSelfController controller = new AdminSelfController(adminService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addMappedInterceptors(
                        new String[]{"/api/admin/me/**"},
                        new AdminAccountSessionInterceptor()
                )
                .build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminSeq", 73L);
        session.setAttribute("adminRole", "reviewer");

        mockMvc.perform(put("/api/admin/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "current-password",
                                  "newPassword": "new-password",
                                  "newPasswordConfirm": "new-password"
                                }
                                """))
                .andExpect(status().isOk());

        verify(adminService).changeOwnPassword(eq(73L), any(AdminPasswordChangeRequest.class));
    }
}
