package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AdminCacheReloadRequest;
import com.bjworld21.conference.dto.AdminCacheReloadResponse;
import com.bjworld21.conference.dto.AdminCacheReloadResult;
import com.bjworld21.conference.service.AdminCacheReloadService;
import com.bjworld21.conference.service.AdminCredentialVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminCacheRecoveryControllerTest {
    private AdminCredentialVerifier credentialVerifier;
    private AdminCacheReloadService cacheReloadService;
    private AdminCacheRecoveryController controller;

    @BeforeEach
    void setUp() {
        credentialVerifier = mock(AdminCredentialVerifier.class);
        cacheReloadService = mock(AdminCacheReloadService.class);
        controller = new AdminCacheRecoveryController(credentialVerifier, cacheReloadService);
    }

    @Test
    void reloadsAllCachesAfterAdministratorAuthentication() {
        LocalDateTime reloadedAt = LocalDateTime.of(2026, 8, 27, 10, 30);
        List<AdminCacheReloadResult> results = List.of(
                new AdminCacheReloadResult("adminIpAllowlist", 3, reloadedAt)
        );
        when(cacheReloadService.reloadAll()).thenReturn(results);

        ResponseEntity<?> response = controller.reload(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new AdminCacheReloadResponse(
                results, "관리자 캐시를 모두 다시 불러왔습니다."));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(credentialVerifier).verifyActiveAdministrator("admin-id", "password");
        verify(cacheReloadService).reloadAll();
    }

    @Test
    void rejectsInvalidAdministratorCredentialsWithoutReloadingCaches() {
        doThrow(new IllegalArgumentException("관리자 인증에 실패했습니다."))
                .when(credentialVerifier)
                .verifyActiveAdministrator("admin-id", "password");

        ResponseEntity<?> response = controller.reload(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("관리자 인증에 실패했습니다.");
        verifyNoInteractions(cacheReloadService);
    }

    private AdminCacheReloadRequest request() {
        return new AdminCacheReloadRequest("admin-id", "password");
    }
}
