package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.AdminAccountSessionInterceptor;
import com.bjworld21.conference.config.AdminIpAccessInterceptor;
import com.bjworld21.conference.config.AdminIpAccessProperties;
import com.bjworld21.conference.config.AdminSessionInterceptor;
import com.bjworld21.conference.config.ConferenceAdminWebMvcConfig;
import com.bjworld21.conference.config.MaintenanceSessionInterceptor;
import com.bjworld21.conference.config.ReviewerSessionInterceptor;
import com.bjworld21.conference.config.SecurityConfig;
import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.service.AdminCacheReloadService;
import com.bjworld21.conference.service.AdminCredentialVerifier;
import com.bjworld21.conference.service.AdminIpAccessCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.InetAddress;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(AdminCacheRecoveryAccessTest.AccessConfig.class)
class AdminCacheRecoveryAccessTest {
    private static final String ALLOWED_IP = "203.0.113.10";
    private static final String DISALLOWED_IP = "203.0.113.11";

    @Autowired private WebApplicationContext context;
    @Autowired private AdminIpAccessCache ipCache;
    @Autowired private AdminCredentialVerifier credentialVerifier;
    @Autowired private AdminCacheReloadService cacheReloadService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(ipCache, credentialVerifier, cacheReloadService);
        when(ipCache.isAllowed(any(InetAddress.class)))
                .thenAnswer(invocation -> ALLOWED_IP.equals(
                        invocation.<InetAddress>getArgument(0).getHostAddress()));
        when(cacheReloadService.reloadAll()).thenReturn(List.of());
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void disallowedIpIsRejectedBeforeCredentialsOrFailureCountersAreTouched() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(reloadFrom(DISALLOWED_IP).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("X-CSRF-ERROR"));
        }
        verifyNoInteractions(credentialVerifier, cacheReloadService);
    }

    @Test
    void existingAdminSessionDoesNotBypassIpRestriction() throws Exception {
        mvc.perform(reloadFrom(DISALLOWED_IP).with(csrf())
                        .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(credentialVerifier, cacheReloadService);
    }

    @Test
    void allowedIpCanReloadWithCredentialsWithoutAnExistingAdminSession() throws Exception {
        mvc.perform(reloadFrom(ALLOWED_IP).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(credentialVerifier).verifyActiveAdministrator("audit-admin", "audit-password");
        verify(cacheReloadService).reloadAll();
    }

    @Test
    void allowedIpStillNeedsValidAdministratorCredentials() throws Exception {
        doThrow(new IllegalArgumentException("Invalid credentials"))
                .when(credentialVerifier).verifyActiveAdministrator("audit-admin", "audit-password");
        mvc.perform(reloadFrom(ALLOWED_IP).with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cacheReloadService);
    }

    @Test
    void allowedIpStillNeedsCsrfToken() throws Exception {
        mvc.perform(reloadFrom(ALLOWED_IP))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-CSRF-ERROR", "true"));
        verifyNoInteractions(credentialVerifier, cacheReloadService);
    }

    @Test
    void untrustedClientCannotSpoofAllowedIpWithForwardedHeader() throws Exception {
        mvc.perform(reloadFrom(DISALLOWED_IP).with(csrf()).header("X-Forwarded-For", ALLOWED_IP))
                .andExpect(status().isForbidden());
        verifyNoInteractions(credentialVerifier, cacheReloadService);
    }

    @Test
    void trustedProxyUsesTheOriginalClientIpForAllowlistCheck() throws Exception {
        mvc.perform(reloadFrom("127.0.0.1").with(csrf()).header("X-Forwarded-For", DISALLOWED_IP))
                .andExpect(status().isForbidden());
        verifyNoInteractions(credentialVerifier, cacheReloadService);

        mvc.perform(reloadFrom("127.0.0.1").with(csrf()).header("X-Forwarded-For", ALLOWED_IP))
                .andExpect(status().isOk());
        verify(credentialVerifier).verifyActiveAdministrator("audit-admin", "audit-password");
        verify(cacheReloadService).reloadAll();
    }

    private MockHttpServletRequestBuilder reloadFrom(String remoteAddress) {
        return post("/api/admin/cache/reload")
                .with(request -> { request.setRemoteAddr(remoteAddress); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"audit-admin\",\"password\":\"audit-password\"}");
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ConferenceAdminWebMvcConfig.class,
            AdminIpAccessInterceptor.class, ClientIpResolver.class,
            AdminSessionInterceptor.class, AdminAccountSessionInterceptor.class,
            ReviewerSessionInterceptor.class, MaintenanceSessionInterceptor.class,
            AdminCacheRecoveryController.class})
    static class AccessConfig {
        @Bean AdminIpAccessProperties ipProperties() {
            AdminIpAccessProperties properties = new AdminIpAccessProperties();
            properties.setEnabled(true);
            properties.setTrustedProxies(List.of("127.0.0.1/32"));
            return properties;
        }
        @Bean AdminIpAccessCache ipCache() { return mock(AdminIpAccessCache.class); }
        @Bean AdminCredentialVerifier credentialVerifier() { return mock(AdminCredentialVerifier.class); }
        @Bean AdminCacheReloadService cacheReloadService() { return mock(AdminCacheReloadService.class); }
    }
}
