package com.bjworld21.conference.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSessionInterceptorTest {
    private AdminSessionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminSessionInterceptor();
    }

    @Test
    void allowsAdminSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminSeq", 1L);
        request.getSession().setAttribute("adminRole", "admin");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void allowsMaintenanceSessionAsFullAdministrator() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminSeq", 2L);
        request.getSession().setAttribute("adminRole", "maintenance");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("관리자 로그인이 필요합니다");
    }

    @Test
    void rejectsReviewerSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminSeq", 2L);
        request.getSession().setAttribute("adminRole", "reviewer");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
