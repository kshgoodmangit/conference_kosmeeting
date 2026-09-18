package com.bjworld21.congress.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class RoleSessionInterceptorTest {
    private final AdminSessionInterceptor adminInterceptor = new AdminSessionInterceptor();
    private final AdminAccountSessionInterceptor adminAccountInterceptor = new AdminAccountSessionInterceptor();
    private final ReviewerSessionInterceptor reviewerInterceptor = new ReviewerSessionInterceptor();

    @Test
    void adminApiAllowsAdminAndMaintenanceRoles() throws Exception {
        MockHttpServletResponse adminResponse = new MockHttpServletResponse();
        assertThat(adminInterceptor.preHandle(request("GET", "/api/admin/abstracts", "admin"), adminResponse, new Object()))
                .isTrue();

        MockHttpServletResponse maintenanceResponse = new MockHttpServletResponse();
        assertThat(adminInterceptor.preHandle(request("GET", "/api/admin/abstracts", "maintenance"), maintenanceResponse, new Object()))
                .isTrue();

        MockHttpServletResponse reviewerResponse = new MockHttpServletResponse();
        assertThat(adminInterceptor.preHandle(request("GET", "/api/admin/abstracts", "reviewer"), reviewerResponse, new Object()))
                .isFalse();
        assertThat(reviewerResponse.getStatus()).isEqualTo(403);
        assertThat(reviewerResponse.getContentAsString()).isEqualTo("관리자 권한이 필요합니다.");
    }

    @Test
    void adminApiReturnsUnauthorizedWithoutLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/abstracts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(adminInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void reviewerApiAllowsOnlyReviewerRole() throws Exception {
        MockHttpServletResponse reviewerResponse = new MockHttpServletResponse();
        assertThat(reviewerInterceptor.preHandle(
                request("GET", "/api/reviewer/reviews", "reviewer"), reviewerResponse, new Object()))
                .isTrue();

        MockHttpServletResponse adminResponse = new MockHttpServletResponse();
        assertThat(reviewerInterceptor.preHandle(
                request("GET", "/api/reviewer/reviews", "admin"), adminResponse, new Object()))
                .isFalse();
        assertThat(adminResponse.getStatus()).isEqualTo(403);
        assertThat(adminResponse.getContentAsString()).isEqualTo("심사자 권한이 필요합니다.");
    }

    @Test
    void reviewerApiRejectsSessionWithoutReviewerConference() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/reviewer/reviews", "reviewer");
        request.getSession().removeAttribute("reviewerConferenceSeq");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(reviewerInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo("심사자 학회 정보가 없습니다. 다시 로그인해 주세요.");
    }

    @Test
    void ownAccountApiAllowsAdminAndReviewerRoles() throws Exception {
        assertThat(adminAccountInterceptor.preHandle(
                request("PUT", "/api/admin/me/password", "admin"),
                new MockHttpServletResponse(),
                new Object()))
                .isTrue();
        assertThat(adminAccountInterceptor.preHandle(
                request("PUT", "/api/admin/me/password", "reviewer"),
                new MockHttpServletResponse(),
                new Object()))
                .isTrue();

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(adminAccountInterceptor.preHandle(
                request("PUT", "/api/admin/me/password", "guest"), response, new Object()))
                .isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void preflightRequestsRemainAvailable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/reviewer/reviews");
        assertThat(reviewerInterceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    private MockHttpServletRequest request(String method, String path, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminSeq", 1L);
        session.setAttribute("adminRole", role);
        if ("reviewer".equals(role)) {
            session.setAttribute("reviewerConferenceSeq", 1L);
        }
        request.setSession(session);
        return request;
    }
}
