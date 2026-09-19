package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AdminPasswordChangeRequest;
import com.bjworld21.conference.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/me")
public class AdminSelfController {
    private final AdminService adminService;

    public AdminSelfController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PutMapping("/password")
    public ResponseEntity<?> changeOwnPassword(
            @RequestBody AdminPasswordChangeRequest request,
            HttpSession session
    ) {
        try {
            Object value = session.getAttribute("adminSeq");
            if (!(value instanceof Number number)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
            }

            adminService.changeOwnPassword(number.longValue(), request);
            session.invalidate();
            return ResponseEntity.ok("비밀번호가 변경되었습니다. 다시 로그인해주세요.");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("비밀번호를 변경하는 중 오류가 발생했습니다.");
        }
    }
}
