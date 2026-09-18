package com.bjworld21.congress.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitWebConfig({SecurityConfig.class, SecurityConfigTest.WebSecurity.class})
class SecurityConfigTest {
    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class WebSecurity {
    }

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Test
    void preparesSessionBeforeLoginPageStartsStreaming() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setServletPath("/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityFilterChain.doFilter(request, response, (req, res) -> {
            assertThat(request.getSession(false)).isNotNull();
            res.flushBuffer();
            CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
            assertThat(token.getToken()).isNotBlank();
        });

        assertThat(response.isCommitted()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void stillRejectsPostWithoutCsrfToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/members/login");
        request.setServletPath("/api/members/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityFilterChain.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Request without CSRF token must not reach the controller");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("X-CSRF-ERROR")).isEqualTo("true");
    }

    @Test
    void accessRequestsAndApprovalsStillRequireCsrf() throws Exception {
        for (String path : new String[]{"/api/access-requests", "/api/admin/access-requests/1/approve", "/api/admin/access-requests/1/reject", "/api/access-requests/1/mail-approval"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setServletPath(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            securityFilterChain.doFilter(request, response, (req, res) -> {
                throw new AssertionError("Missing CSRF must not reach access request handlers");
            });
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }
}
