package com.bjworld21.conference.controller;

import com.bjworld21.conference.publicsite.PublicApiRequest;

import com.bjworld21.conference.service.BoardPostService;
import com.bjworld21.conference.service.MenuSettingsService;
import com.bjworld21.conference.dto.MenuSettingsResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@com.bjworld21.conference.config.IpAccessExempt
@RequestMapping("/api/public/{conferenceSeq}/boards")
public class PublicBoardAttachmentController {
    private final BoardPostService service;
    private final MenuSettingsService menus;

    public PublicBoardAttachmentController(BoardPostService service,
                                           MenuSettingsService menus) {
        this.service = service;
        this.menus = menus;
    }

    @GetMapping("/{boardSeq}/posts/{postSeq}/attachments/{attachmentSeq}")
    public ResponseEntity<Resource> download(
            @PathVariable Long boardSeq,
            @PathVariable Long postSeq,
            @PathVariable Long attachmentSeq,
            HttpServletRequest request
    ) {
        var site = PublicApiRequest.context();
        Boolean authRequired = boardAuth(menus.getActiveUserMenuTree(site.conferenceSeq(), site.language()), boardSeq, false);
        if (authRequired == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (authRequired && PublicMemberSession.resolve(request.getSession(false), site.conferenceSeq()) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        BoardPostService.AttachmentDownload download = service.downloadPublicAttachment(
                site.conferenceSeq(),
                boardSeq,
                postSeq,
                attachmentSeq
        );
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.fileSize())
                .body(download.resource());
    }

    /** A direct attachment URL must honor the same active menu and ancestor protection as its page. */
    private Boolean boardAuth(List<MenuSettingsResponse> nodes, Long boardSeq, boolean protectedParent) {
        Boolean result = null;
        if (nodes == null) return null;
        for (var menu : nodes) {
            if (Boolean.FALSE.equals(menu.getEnabled())) continue;
            boolean protectedMenu = protectedParent || Boolean.TRUE.equals(menu.getAuthRequired());
            Long targetBoard = menu.getBoardSeq();
            if (targetBoard == null) {
                if ("notice".equals(menu.getMenuKey())) targetBoard = BoardPostService.NOTICE_BOARD_SEQ;
                else if ("faq".equals(menu.getMenuKey())) targetBoard = BoardPostService.FAQ_BOARD_SEQ;
            }
            if (boardSeq.equals(targetBoard)) {
                result = Boolean.TRUE.equals(result) || protectedMenu;
            }
            Boolean child = boardAuth(menu.getChildren(), boardSeq, protectedMenu);
            if (child != null) result = Boolean.TRUE.equals(result) || child;
        }
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException exception) {
        return exception.getMessage();
    }
}
