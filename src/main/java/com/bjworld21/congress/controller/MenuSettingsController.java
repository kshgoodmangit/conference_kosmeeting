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
    private com.bjworld21.congress.service.MenuTranslationService translationService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTranslationService(com.bjworld21.congress.service.MenuTranslationService service) {
        this.translationService = service;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidLanguageRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    public MenuSettingsController(MenuSettingsService menuSettingsService, MenuHtmlHistoryService historyService) {
        this.menuSettingsService = menuSettingsService;
        this.historyService = historyService;
    }

    @GetMapping("/tree")
    public ResponseEntity<List<MenuSettingsResponse>> getMenuTree(@RequestHeader("X-Conference-Seq") Long conferenceSeq, @RequestParam(required=false) String language) {
        return ResponseEntity.ok(language == null ? menuSettingsService.getMenuTree(conferenceSeq) : menuSettingsService.getMenuTree(conferenceSeq, language));
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
            @RequestParam(required=false) String language,
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
                    changeMemo,
                    language
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
            @RequestParam(required=false) String language,
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
                    menuHtmlChanged,
                    language
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
                                         @RequestParam(defaultValue = "20") int size, @RequestParam(required=false) String language) {
        return ResponseEntity.ok(translationService == null ? historyService.list(conferenceSeq, seq, page, size)
                : translationService.list(conferenceSeq, seq, language == null ? "en" : language, page, size));
    }

    @GetMapping("/{seq}/html-histories/{historySeq}")
    public ResponseEntity<?> getHistory(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                       @PathVariable Long seq, @PathVariable Long historySeq, @RequestParam(required=false) String language) {
        return ResponseEntity.ok(translationService == null ? historyService.detail(conferenceSeq, seq, historySeq)
                : translationService.detail(conferenceSeq, seq, language == null ? "en" : language, historySeq));
    }

    public record RestoreRequest(String changeMemo) {}

    @PostMapping("/{seq}/html-histories/{historySeq}/restore")
    public ResponseEntity<?> restoreHtml(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @PathVariable Long seq, @PathVariable Long historySeq,
                                        @RequestBody(required = false) RestoreRequest request,
                                        @RequestParam(required=false) String language, HttpSession session) {
        try {
            return ResponseEntity.ok(translationService == null ? menuSettingsService.restoreHtml(conferenceSeq, seq, historySeq,
                    currentAdminSeq(session), request == null ? null : request.changeMemo()) : translationService.restore(conferenceSeq, seq,
                    language == null ? "en" : language, historySeq, currentAdminSeq(session), request == null ? null : request.changeMemo()));
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
                                          @RequestParam(required=false) String language,
                                          @RequestBody MenuReorderRequest request) {
        try {
            var result = menuSettingsService.reorder(conferenceSeq, request != null ? request.getItems() : null);
            return ResponseEntity.ok(language == null ? result : menuSettingsService.getMenuTree(conferenceSeq, language));
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
