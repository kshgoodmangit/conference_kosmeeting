package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.MenuReorderRequest;
import com.bjworld21.congress.dto.MenuSettingsResponse;
import com.bjworld21.congress.service.MenuSettingsService;
import com.bjworld21.congress.service.MenuHtmlHistoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/menu-settings")
public class MenuSettingsController {
    private final MenuSettingsService menuSettingsService;
    private final MenuHtmlHistoryService historyService;

    public MenuSettingsController(MenuSettingsService menuSettingsService, MenuHtmlHistoryService historyService) {
        this.menuSettingsService = menuSettingsService;
        this.historyService = historyService;
    }

    @GetMapping("/tree")
    public ResponseEntity<List<MenuSettingsResponse>> getMenuTree(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return ResponseEntity.ok(menuSettingsService.getMenuTree(conferenceSeq));
    }

    @PostMapping
    public ResponseEntity<?> createMenu(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam String menuScope,
            @RequestParam String menuKey,
            @RequestParam(required = false) String parentKey,
            @RequestParam String menuName,
            @RequestParam(required = false) String menuPath,
            @RequestParam(defaultValue = "folder") String menuType,
            @RequestParam(defaultValue = "segment") String pathType,
            @RequestParam(required = false) String routePath,
            @RequestParam(required = false) Long boardSeq,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(defaultValue = "self") String targetType,
            @RequestParam(defaultValue = "false") Boolean authRequired,
            @RequestParam(defaultValue = "true") Boolean navigationVisible,
            @RequestParam(required = false) String menuHtml,
            @RequestParam(defaultValue = "0") Integer sortOrder,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useEndDate,
            @RequestParam(defaultValue = "true") Boolean enabled,
            @RequestParam(required = false) String changeMemo,
            HttpSession session
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(menuSettingsService.create(
                    conferenceSeq,
                    menuScope,
                    menuKey,
                    parentKey,
                    menuName,
                    menuPath,
                    menuType,
                    pathType,
                    routePath,
                    boardSeq,
                    linkUrl,
                    targetType,
                    authRequired,
                    navigationVisible,
                    menuHtml,
                    sortOrder,
                    useStartDate,
                    useEndDate,
                    enabled,
                    currentAdminSeq(session),
                    changeMemo
            ));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("메뉴 등록 중 오류가 발생했습니다.");
        }
    }

    @PutMapping("/{seq}")
    public ResponseEntity<?> updateMenu(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestParam String menuName,
            @RequestParam(required = false) String menuPath,
            @RequestParam(defaultValue = "folder") String menuType,
            @RequestParam(defaultValue = "segment") String pathType,
            @RequestParam(required = false) String routePath,
            @RequestParam(required = false) Long boardSeq,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(defaultValue = "self") String targetType,
            @RequestParam(defaultValue = "false") Boolean authRequired,
            @RequestParam(defaultValue = "true") Boolean navigationVisible,
            @RequestParam(required = false) String menuHtml,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate useEndDate,
            @RequestParam(defaultValue = "false") Boolean enabled,
            @RequestParam(required = false) String changeMemo,
            @RequestParam(defaultValue = "true") boolean menuHtmlChanged,
            HttpSession session
    ) {
        try {
            return ResponseEntity.ok(menuSettingsService.update(
                    conferenceSeq,
                    seq,
                    menuName,
                    menuPath,
                    menuType,
                    pathType,
                    routePath,
                    boardSeq,
                    linkUrl,
                    targetType,
                    authRequired,
                    navigationVisible,
                    menuHtml,
                    useStartDate,
                    useEndDate,
                    enabled,
                    currentAdminSeq(session),
                    changeMemo,
                    menuHtmlChanged
            ));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("메뉴 설정 저장 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/{seq}/html-histories")
    public ResponseEntity<?> getHistories(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                         @PathVariable Long seq,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(historyService.list(conferenceSeq, seq, page, size));
    }

    @GetMapping("/{seq}/html-histories/{historySeq}")
    public ResponseEntity<?> getHistory(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                       @PathVariable Long seq, @PathVariable Long historySeq) {
        return ResponseEntity.ok(historyService.detail(conferenceSeq, seq, historySeq));
    }

    public record RestoreRequest(String changeMemo) {}

    @PostMapping("/{seq}/html-histories/{historySeq}/restore")
    public ResponseEntity<?> restoreHtml(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @PathVariable Long seq, @PathVariable Long historySeq,
                                        @RequestBody(required = false) RestoreRequest request,
                                        HttpSession session) {
        try {
            return ResponseEntity.ok(menuSettingsService.restoreHtml(conferenceSeq, seq, historySeq,
                    currentAdminSeq(session), request == null ? null : request.changeMemo()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("HTML 복원 중 오류가 발생했습니다.");
        }
    }

    private Long currentAdminSeq(HttpSession session) {
        if (!(session.getAttribute("adminSeq") instanceof Number adminSeq)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "관리자 로그인이 필요합니다.");
        }
        return adminSeq.longValue();
    }

    @PutMapping("/reorder")
    public ResponseEntity<?> reorderMenus(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                          @RequestBody MenuReorderRequest request) {
        try {
            return ResponseEntity.ok(menuSettingsService.reorder(conferenceSeq, request != null ? request.getItems() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("메뉴 정렬 저장 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<?> deleteMenu(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @PathVariable Long seq) {
        try {
            menuSettingsService.delete(conferenceSeq, seq);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("메뉴 삭제 중 오류가 발생했습니다.");
        }
    }
}
