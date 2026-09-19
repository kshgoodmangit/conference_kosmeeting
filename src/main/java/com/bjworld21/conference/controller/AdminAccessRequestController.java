package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.dto.AdminAccessRequestInput;
import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.security.RequestSiteUrlResolver;
import com.bjworld21.conference.service.AdminAccessRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.Map;

@RestController
public class AdminAccessRequestController {
    private final AdminAccessRequestService service;
    private final ClientIpResolver ipResolver;
    private final RequestSiteUrlResolver siteUrlResolver;

    public AdminAccessRequestController(AdminAccessRequestService service, ClientIpResolver ipResolver, RequestSiteUrlResolver siteUrlResolver) {
        this.service = service;
        this.ipResolver = ipResolver;
        this.siteUrlResolver = siteUrlResolver;
    }

    // Only these two public methods are exempt from IP checks; admin list/approval remain protected.
    @IpAccessExempt
    @GetMapping("/api/access-requests/info")
    public ResponseEntity<?> info(HttpServletRequest request) {
        String siteUrl = siteUrlResolver.resolve(request);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("siteUrl", siteUrl, "domain", URI.create(siteUrl).getAuthority(), "clientIp", ipResolver.resolve(request)));
    }

    @IpAccessExempt
    @PostMapping("/api/access-requests")
    public ResponseEntity<?> submit(@Valid @RequestBody AdminAccessRequestInput input, HttpServletRequest request) {
        var result = service.submit(input, ipResolver.resolve(request), siteUrlResolver.resolve(request));
        return ResponseEntity.status(result.duplicate() ? 200 : 201).body(result);
    }

    @GetMapping("/api/admin/access-requests")
    public ResponseEntity<?> list(@RequestParam(defaultValue="") String keyword, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.findPage(keyword, status, page, size));
    }

    @PostMapping("/api/admin/access-requests/{seq}/approve")
    public ResponseEntity<?> approve(@PathVariable Long seq, HttpSession session) {
        service.approve(seq, adminSeq(session));
        return ResponseEntity.ok(Map.of("message", "접근 허용 IP에 등록했습니다."));
    }

    @PostMapping("/api/admin/access-requests/{seq}/reject")
    public ResponseEntity<?> reject(@PathVariable Long seq, HttpSession session) {
        service.reject(seq, adminSeq(session));
        return ResponseEntity.ok(Map.of("message", "요청을 거절했습니다."));
    }

    private Long adminSeq(HttpSession session) {
        if (!(session.getAttribute("adminSeq") instanceof Number value)) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "관리자 로그인이 필요합니다.");
        return value.longValue();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> statusError(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<String> validationError(Exception e) {
        return ResponseEntity.badRequest().body(e instanceof IllegalArgumentException ? e.getMessage() : "필수 입력항목과 입력 형식을 확인해 주세요.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> storageError() {
        return ResponseEntity.status(503).body("요청을 처리하지 못했습니다. 잠시 후 목록 또는 접수 상태를 다시 확인해 주세요.");
    }
}
