package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.IpAccessExempt;
import com.bjworld21.congress.dto.AdminClientIpResponse;
import com.bjworld21.congress.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access-info")
public class AdminAccessInfoController {
    private final ClientIpResolver clientIpResolver;

    public AdminAccessInfoController(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/client-ip")
    @IpAccessExempt
    public ResponseEntity<AdminClientIpResponse> getClientIp(HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AdminClientIpResponse(clientIpResolver.resolve(request)));
    }
}
