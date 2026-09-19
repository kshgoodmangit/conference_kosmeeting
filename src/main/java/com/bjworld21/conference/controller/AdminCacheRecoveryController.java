package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AdminCacheReloadRequest;
import com.bjworld21.conference.dto.AdminCacheReloadResponse;
import com.bjworld21.conference.dto.AdminCacheReloadResult;
import com.bjworld21.conference.service.AdminCacheReloadService;
import com.bjworld21.conference.service.AdminCredentialVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cache")
public class AdminCacheRecoveryController {
    private static final Logger log = LoggerFactory.getLogger(AdminCacheRecoveryController.class);

    private final AdminCredentialVerifier credentialVerifier;
    private final AdminCacheReloadService cacheReloadService;

    public AdminCacheRecoveryController(
            AdminCredentialVerifier credentialVerifier,
            AdminCacheReloadService cacheReloadService
    ) {
        this.credentialVerifier = credentialVerifier;
        this.cacheReloadService = cacheReloadService;
    }

    @PostMapping("/reload")
    public ResponseEntity<?> reload(@RequestBody AdminCacheReloadRequest request) {
        String email = request == null ? null : request.email();
        String password = request == null ? null : request.password();
        try {
            credentialVerifier.verifyActiveAdministrator(email, password);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body("관리자 인증에 실패했습니다.");
        }

        try {
            List<AdminCacheReloadResult> results = cacheReloadService.reloadAll();
            log.info("Admin caches reloaded through authenticated recovery endpoint: admin={}, caches={}",
                    email.trim(), results.stream().map(AdminCacheReloadResult::cacheName).toList());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new AdminCacheReloadResponse(results, "관리자 캐시를 모두 다시 불러왔습니다."));
        } catch (Exception exception) {
            log.error("Failed to reload admin caches through authenticated recovery endpoint", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .cacheControl(CacheControl.noStore())
                    .body("관리자 캐시를 다시 불러오지 못했습니다.");
        }
    }
}
