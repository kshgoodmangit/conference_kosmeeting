package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.dto.CsrfTokenResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security")
public class CsrfTokenController {

    @GetMapping("/csrf-token")
    @IpAccessExempt
    public ResponseEntity<CsrfTokenResponse> getCsrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken()));
    }
}
