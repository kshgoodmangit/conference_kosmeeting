package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AdminRolePolicy;
import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.entity.MenuHtmlHistory;
import com.bjworld21.congress.entity.MenuSettings;
import com.bjworld21.congress.repository.AdminAccountRepository;
import com.bjworld21.congress.repository.MenuHtmlHistoryRepository;
import com.bjworld21.congress.repository.MenuSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class MenuHtmlHistoryService {
    private final MenuHtmlHistoryRepository histories;
    private final MenuSettingsRepository menus;
    private final AdminAccountRepository admins;
    private final PersonalDataProperties personalData;
    private final CmsHtmlSanitizer sanitizer;

    public MenuHtmlHistoryService(MenuHtmlHistoryRepository histories, MenuSettingsRepository menus,
                                 AdminAccountRepository admins, PersonalDataProperties personalData,
                                 CmsHtmlSanitizer sanitizer) {
        this.histories = histories;
        this.menus = menus;
        this.admins = admins;
        this.personalData = personalData;
        this.sanitizer = sanitizer;
    }

    public record HistoryPage(List<MenuHtmlHistory> items, long total, int page, int size,
                              long currentRevisionNo) {}
    public record HistoryDetail(MenuHtmlHistory history, String previewHtml,
                                String currentHtml, long currentRevisionNo) {}

    @Transactional(readOnly = true)
    public HistoryPage list(Long conferenceSeq, Long menuSeq, int page, int size) {
        MenuSettings menu = requireMenu(conferenceSeq, menuSeq);
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이력 페이지를 요청해 주세요.");
        }
        return new HistoryPage(histories.findPage(menuSeq, size, (long) page * size),
                histories.count(menuSeq), page, size, revision(menu));
    }

    @Transactional(readOnly = true)
    public HistoryDetail detail(Long conferenceSeq, Long menuSeq, Long historySeq) {
        MenuSettings menu = requireMenu(conferenceSeq, menuSeq);
        MenuHtmlHistory history = requireHistory(menuSeq, historySeq);
        return new HistoryDetail(history, sanitizer.sanitize(history.getMenuHtml()),
                menu.getMenuHtml(), revision(menu));
    }

    public MenuHtmlHistory requireHistory(Long menuSeq, Long historySeq) {
        MenuHtmlHistory history = histories.findBySeq(menuSeq, historySeq);
        if (history == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 메뉴의 HTML 이력이 없습니다.");
        }
        return history;
    }

    private MenuSettings requireMenu(Long conferenceSeq, Long menuSeq) {
        MenuSettings menu = menus.findBySeq(conferenceSeq, menuSeq);
        if (menu == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다.");
        }
        return menu;
    }

    // The caller's transaction holds the menu row lock and writes the current
    // HTML together with its immutable snapshot. No separate commit is allowed.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(MenuSettings menu, String operation, Long sourceSeq, String memo, Long adminSeq) {
        String normalizedMemo = normalizeMemo(memo);
        var admin = adminSeq == null ? null : admins.findBySeq(adminSeq, personalData.requireDbEncString());
        if (admin == null || !"active".equals(admin.getStatus())
                || !AdminRolePolicy.isFullAdministrator(admin.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "유효한 관리자 계정이 필요합니다.");
        }
        histories.insert(MenuHtmlHistory.builder()
                .languageCode(menu.getLanguageCode()).menuSeq(menu.getSeq()).revisionNo(menu.getHtmlRevisionNo()).menuHtml(menu.getMenuHtml())
                .operationType(operation).restoredFromSeq(sourceSeq).changeMemo(normalizedMemo)
                .createdBy(admin.getSeq()).createdByName(admin.getAdminName()).build());
    }

    public boolean hasHistory(Long menuSeq) {
        return histories.count(menuSeq) > 0;
    }

    public static long revision(MenuSettings menu) {
        return menu.getHtmlRevisionNo() == null ? 0 : menu.getHtmlRevisionNo();
    }

    public static String normalizeMemo(String memo) {
        if (memo != null && memo.length() > 500) {
            throw new IllegalArgumentException("변경 메모는 500자 이하로 입력해 주세요.");
        }
        return memo == null || memo.isBlank() ? null : memo.trim();
    }
}
