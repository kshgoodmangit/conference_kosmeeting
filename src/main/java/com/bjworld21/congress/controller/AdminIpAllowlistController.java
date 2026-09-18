package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.AdminIpAccessProperties;
import com.bjworld21.congress.dto.AdminIpAccessStatusResponse;
import com.bjworld21.congress.dto.AdminIpAllowlistRequest;
import com.bjworld21.congress.dto.AdminIpAllowlistResponse;
import com.bjworld21.congress.security.ClientIpResolver;
import com.bjworld21.congress.service.AdminIpAccessCache;
import com.bjworld21.congress.service.AdminIpAllowlistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ip-allowlist")
public class AdminIpAllowlistController {
    private final AdminIpAllowlistService service;
    private final AdminIpAccessCache cache;
    private final AdminIpAccessProperties properties;
    private final ClientIpResolver clientIpResolver;

    public AdminIpAllowlistController(
            AdminIpAllowlistService service,
            AdminIpAccessCache cache,
            AdminIpAccessProperties properties,
            ClientIpResolver clientIpResolver
    ) {
        this.service = service;
        this.cache = cache;
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    public ResponseEntity<List<AdminIpAllowlistResponse>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/status")
    public ResponseEntity<AdminIpAccessStatusResponse> status(HttpServletRequest request) {
        return ResponseEntity.ok(statusResponse(request));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody AdminIpAllowlistRequest request,
            HttpSession session
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.create(request, adminSeq(session)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PutMapping("/{seq}")
    public ResponseEntity<?> update(
            @PathVariable Long seq,
            @RequestBody AdminIpAllowlistRequest request,
            HttpSession session
    ) {
        try {
            return ResponseEntity.ok(service.update(seq, request, adminSeq(session)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<?> delete(@PathVariable Long seq) {
        try {
            service.delete(seq);
            return ResponseEntity.ok("접근 허용 IP 규칙이 삭제되었습니다.");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
        }
    }

    @PostMapping("/reload")
    public ResponseEntity<?> reload(HttpServletRequest request) {
        try {
            cache.reload();
            return ResponseEntity.ok(statusResponse(request));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("접근 허용 IP 캐시를 다시 불러오지 못했습니다.");
        }
    }

    private AdminIpAccessStatusResponse statusResponse(HttpServletRequest request) {
        return AdminIpAccessStatusResponse.builder()
                .enabled(properties.isEnabled())
                .cachedRuleCount(cache.getCachedRuleCount())
                .currentIp(clientIpResolver.resolve(request))
                .reloadedAt(cache.getReloadedAt())
                .build();
    }

    private Long adminSeq(HttpSession session) {
        Object value = session.getAttribute("adminSeq");
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("관리자 로그인 정보가 없습니다.");
        }
        return number.longValue();
    }
}
