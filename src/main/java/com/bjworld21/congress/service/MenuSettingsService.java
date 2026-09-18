package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.MenuReorderItemRequest;
import com.bjworld21.congress.dto.MenuSettingsResponse;
import com.bjworld21.congress.entity.MenuSettings;
import com.bjworld21.congress.repository.MenuSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MenuSettingsService {
    private final MenuSettingsRepository menuSettingsRepository;
    private final MenuHtmlHistoryService historyService;
    private MenuTranslationService translationService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTranslationService(MenuTranslationService service) { this.translationService=service; }

    public MenuSettingsService(MenuSettingsRepository menuSettingsRepository, MenuHtmlHistoryService historyService) {
        this.menuSettingsRepository = menuSettingsRepository;
        this.historyService = historyService;
    }

    public List<MenuSettingsResponse> getMenuTree(Long conferenceSeq) {
        return buildMenuTree(menuSettingsRepository.findAll(conferenceSeq), false);
    }

    public List<MenuSettingsResponse> getActiveUserMenuTree(Long conferenceSeq) {
        return buildMenuTree(menuSettingsRepository.findActiveByScope(conferenceSeq, "user", LocalDate.now()), true);
    }

    public List<MenuSettingsResponse> getMenuTree(Long conferenceSeq, String language) {
        return buildMenuTree(translationService.localize(conferenceSeq, menuSettingsRepository.findAll(conferenceSeq), language), false);
    }

    public List<MenuSettingsResponse> getActiveUserMenuTree(Long conferenceSeq, String language) {
        return buildMenuTree(translationService.localize(conferenceSeq,
                menuSettingsRepository.findActiveByScope(conferenceSeq, "user", LocalDate.now()), language), true);
    }

    private List<MenuSettingsResponse> buildMenuTree(List<MenuSettings> menus, boolean unwrapConfiguredRoot) {
        Map<String, MenuSettingsResponse> nodeMap = new LinkedHashMap<>();
        List<MenuSettingsResponse> roots = new ArrayList<>();
        Map<String, Long> publicRoutes = new HashMap<>();

        for (MenuSettings menu : menus) {
            MenuSettingsResponse response = toResponse(menu);
            if (unwrapConfiguredRoot && "user".equals(menu.getMenuScope()) && !"link".equals(menu.getMenuType())) {
                String route = publicMenuRoute(menu);
                if (route != null && publicRoutes.putIfAbsent(route, menu.getSeq()) != null) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Duplicate public menu route: " + route);
                }
                response.setRoutePath(route);
            }
            response.setChildren(new ArrayList<>());
            nodeMap.put(nodeKey(menu.getMenuScope(), menu.getMenuKey()), response);
        }

        for (MenuSettings menu : menus) {
            MenuSettingsResponse node = nodeMap.get(nodeKey(menu.getMenuScope(), menu.getMenuKey()));
            if (menu.getParentKey() == null || menu.getParentKey().isBlank()) {
                roots.add(node);
                continue;
            }

            MenuSettingsResponse parent = nodeMap.get(nodeKey(menu.getMenuScope(), menu.getParentKey()));
            if (parent == null) {
                // Public descendants of a disabled/expired ancestor must remain
                // unreachable. The admin tree keeps orphan nodes editable.
                if (!unwrapConfiguredRoot) roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }

        if (!unwrapConfiguredRoot) {
            return roots;
        }

        return roots.stream()
                .filter(menu -> "root".equals(menu.getMenuKey()))
                .findFirst()
                .map(root -> {
                    // The configured root is removed from public navigation, so
                    // retain its authentication requirement in each visible lineage.
                    if (Boolean.TRUE.equals(root.getAuthRequired())) {
                        root.getChildren().forEach(child -> child.setAuthRequired(true));
                    }
                    return root.getChildren();
                })
                .orElseGet(() -> roots.stream()
                        .filter(menu -> !"root".equals(menu.getMenuKey()))
                        .toList());
    }

    private String publicMenuRoute(MenuSettings menu) {
        String route = normalizeOptional(menu.getRoutePath());
        // Older rows stored the parent folder in routePath even though menuPath
        // already identified the page. Use that stored page name for both public
        // navigation and page lookup; never infer it from a hard-coded route map.
        if (route != null && route.matches("/[a-z0-9-]+(?:/[a-z0-9-]+)+/?")
                && normalizeOptional(menu.getMenuPath()) != null) {
            return normalizeRoutePath("user", "full", normalizeOptional(menu.getMenuPath()), null, null);
        }
        return route;
    }

    @Transactional
    public MenuSettingsResponse create(
            Long conferenceSeq,
            String menuScope,
            String menuKey,
            String parentKey,
            String menuName,
            String menuPath,
            String menuType,
            String pathType,
            String routePath,
            Long boardSeq,
            String linkUrl,
            String targetType,
            Boolean authRequired,
            Boolean navigationVisible,
            String menuHtml,
            Integer sortOrder,
            LocalDate useStartDate,
            LocalDate useEndDate,
            Boolean enabled,
            Long adminSeq,
            String changeMemo
    ) {
        return create(conferenceSeq, menuScope, menuKey, parentKey, menuName, menuPath, menuType, pathType, routePath, boardSeq, linkUrl, targetType, authRequired, navigationVisible, menuHtml, sortOrder, useStartDate, useEndDate, enabled, adminSeq, changeMemo, null);
    }

    @Transactional
    public MenuSettingsResponse create(
            Long conferenceSeq,
            String menuScope,
            String menuKey,
            String parentKey,
            String menuName,
            String menuPath,
            String menuType,
            String pathType,
            String routePath,
            Long boardSeq,
            String linkUrl,
            String targetType,
            Boolean authRequired,
            Boolean navigationVisible,
            String menuHtml,
            Integer sortOrder,
            LocalDate useStartDate,
            LocalDate useEndDate,
            Boolean enabled,
            Long adminSeq,
            String changeMemo,
            String language
    ) {
        boolean localized = translationService != null && "user".equals(menuScope);
        if (localized && language == null) language = "en";
        if (localized) translationService.requireLanguage(conferenceSeq, language);
        String normalizedScope = normalizeScope(menuScope);
        String normalizedKey = normalizeRequired(menuKey, "메뉴 키는 필수입니다.");
        String normalizedParentKey = normalizeOptional(parentKey);
        String normalizedName = normalizeRequired(menuName, "메뉴명은 필수입니다.");
        String normalizedMenuPath = normalizeOptional(menuPath);
        String normalizedMenuType = normalizeMenuType(menuType);
        String normalizedPathType = resolvePathType(normalizedMenuType);
        String normalizedTargetType = normalizeTargetType(targetType);
        String normalizedLinkUrl = normalizeLinkUrl(linkUrl);
        String normalizedMenuHtml = normalizeOptional(menuHtml);

        validateUsePeriod(useStartDate, useEndDate);

        if (menuSettingsRepository.findByScopeAndKey(conferenceSeq, normalizedScope, normalizedKey) != null) {
            throw new IllegalArgumentException("이미 등록된 메뉴 키입니다.");
        }

        MenuSettings parent = null;
        if (normalizedParentKey != null
                && (parent = menuSettingsRepository.findByScopeAndKey(conferenceSeq, normalizedScope, normalizedParentKey)) == null) {
            throw new IllegalArgumentException("상위 메뉴가 존재하지 않습니다.");
        }

        int depth = resolveStoredDepth(parent);
        validateMaxDepth(depth);
        String normalizedRoutePath = normalizeRoutePath(normalizedScope, normalizedPathType, normalizedMenuPath, routePath, normalizedLinkUrl);
        if (localized) translationService.validatePagePath(conferenceSeq, normalizedRoutePath);
        validateMenuTypeDependencies(normalizedMenuType, boardSeq, normalizedLinkUrl);
        validateRoutePathDuplicate(conferenceSeq, normalizedScope, normalizedRoutePath, null);

        MenuSettings menu = MenuSettings.builder()
                .conferenceSeq("user".equals(normalizedScope) ? conferenceSeq : null)
                .menuScope(normalizedScope)
                .menuKey(normalizedKey)
                .parentKey(normalizedParentKey)
                .menuName(localized && !"en".equals(language) ? normalizedKey : normalizedName)
                .menuPath(normalizedMenuPath != null ? normalizedMenuPath : normalizedKey)
                .menuType(normalizedMenuType)
                .pathType(normalizedPathType)
                .routePath(normalizedRoutePath)
                .depth(depth)
                .boardSeq(boardSeq)
                .linkUrl(normalizedLinkUrl)
                .targetType(normalizedTargetType)
                .authRequired(authRequired != null ? authRequired : Boolean.FALSE)
                .navigationVisible(navigationVisible != null ? navigationVisible : Boolean.TRUE)
                .menuHtml(localized ? null : normalizedMenuHtml)
                .htmlRevisionNo(localized ? 0L : 1L)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .useStartDate(useStartDate)
                .useEndDate(useEndDate)
                .enabled(enabled != null ? enabled : Boolean.TRUE)
                .build();

        menuSettingsRepository.insert(menu);
        if (localized) {
            translationService.save(conferenceSeq, menu.getSeq(), language, menuName, menuHtml, true, changeMemo, adminSeq);
            translationService.localize(conferenceSeq, List.of(menu), language);
        } else historyService.record(menu, "INITIAL", null, changeMemo, adminSeq);
        return toResponse(menu);
    }

    @Transactional
    public MenuSettingsResponse update(
            Long conferenceSeq,
            Long seq,
            String menuName,
            String menuPath,
            String menuType,
            String pathType,
            String routePath,
            Long boardSeq,
            String linkUrl,
            String targetType,
            Boolean authRequired,
            Boolean navigationVisible,
            String menuHtml,
            LocalDate useStartDate,
            LocalDate useEndDate,
            Boolean enabled,
            Long adminSeq,
            String changeMemo,
            boolean menuHtmlChanged
    ) {
        return update(conferenceSeq, seq, menuName, menuPath, menuType, pathType, routePath, boardSeq, linkUrl, targetType, authRequired, navigationVisible, menuHtml, useStartDate, useEndDate, enabled, adminSeq, changeMemo, menuHtmlChanged, null);
    }

    @Transactional
    public MenuSettingsResponse update(
            Long conferenceSeq,
            Long seq,
            String menuName,
            String menuPath,
            String menuType,
            String pathType,
            String routePath,
            Long boardSeq,
            String linkUrl,
            String targetType,
            Boolean authRequired,
            Boolean navigationVisible,
            String menuHtml,
            LocalDate useStartDate,
            LocalDate useEndDate,
            Boolean enabled,
            Long adminSeq,
            String changeMemo,
            boolean menuHtmlChanged,
            String language
    ) {
        MenuSettings menu = menuSettingsRepository.findBySeqForUpdate(conferenceSeq, seq);
        if (menu == null) {
            throw new IllegalArgumentException("존재하지 않는 메뉴입니다.");
        }

        boolean localized = translationService != null && "user".equals(menu.getMenuScope());
        if (localized && language == null) language = "en";
        if (localized) translationService.requireLanguage(conferenceSeq, language);
        String normalizedName = localized ? menu.getMenuName() : normalizeRequired(menuName, "메뉴명은 필수입니다.");
        String normalizedMenuPath = normalizeOptional(menuPath);
        String normalizedMenuType = normalizeMenuType(menuType);
        String normalizedPathType = resolvePathType(normalizedMenuType);
        String normalizedTargetType = normalizeTargetType(targetType);
        String normalizedLinkUrl = normalizeLinkUrl(linkUrl);
        String normalizedMenuHtml = !localized && menuHtmlChanged ? normalizeOptional(menuHtml) : menu.getMenuHtml();
        boolean htmlChanged = !Objects.equals(normalizeOptional(menu.getMenuHtml()), normalizeOptional(normalizedMenuHtml));
        MenuHtmlHistoryService.normalizeMemo(changeMemo);
        validateUsePeriod(useStartDate, useEndDate);
        validateMenuTypeDependencies(normalizedMenuType, boardSeq, normalizedLinkUrl);

        MenuSettings parent = normalizeOptional(menu.getParentKey()) != null
                ? menuSettingsRepository.findByScopeAndKey(conferenceSeq, menu.getMenuScope(), menu.getParentKey())
                : null;
        int depth = resolveStoredDepth(parent);
        validateMaxDepth(depth);
        String normalizedRoutePath = normalizeRoutePath(menu.getMenuScope(), normalizedPathType, normalizedMenuPath, routePath, normalizedLinkUrl);
        if (localized) translationService.validatePagePath(conferenceSeq, normalizedRoutePath);
        validateRoutePathDuplicate(conferenceSeq, menu.getMenuScope(), normalizedRoutePath, menu.getSeq());

        menu.setMenuName(normalizedName);
        menu.setMenuPath(normalizedMenuPath != null ? normalizedMenuPath : menu.getMenuKey());
        menu.setMenuType(normalizedMenuType);
        menu.setPathType(normalizedPathType);
        menu.setRoutePath(normalizedRoutePath);
        menu.setDepth(depth);
        menu.setBoardSeq(boardSeq);
        menu.setLinkUrl(normalizedLinkUrl);
        menu.setTargetType(normalizedTargetType);
        menu.setAuthRequired(authRequired != null ? authRequired : Boolean.FALSE);
        menu.setNavigationVisible(navigationVisible != null ? navigationVisible : Boolean.TRUE);
        if (htmlChanged) {
            menu.setMenuHtml(normalizedMenuHtml);
            menu.setHtmlRevisionNo(MenuHtmlHistoryService.revision(menu) + 1);
            historyService.record(menu, "SAVE", null, changeMemo, adminSeq);
        }
        menu.setUseStartDate(useStartDate);
        menu.setUseEndDate(useEndDate);
        menu.setEnabled(enabled != null ? enabled : Boolean.FALSE);
        menuSettingsRepository.update(menu);
        if (localized) {
            translationService.save(conferenceSeq, seq, language, menuName, menuHtml, menuHtmlChanged, changeMemo, adminSeq);
            translationService.localize(conferenceSeq, List.of(menu), language);
        }
        return toResponse(menu);
    }

    @Transactional
    public MenuSettingsResponse restoreHtml(Long conferenceSeq, Long seq, Long historySeq,
                                            Long adminSeq, String changeMemo) {
        MenuSettings menu = menuSettingsRepository.findBySeqForUpdate(conferenceSeq, seq);
        if (menu == null) {
            throw new IllegalArgumentException("존재하지 않는 메뉴입니다.");
        }
        var source = historyService.requireHistory(seq, historySeq);
        MenuHtmlHistoryService.normalizeMemo(changeMemo);
        if (Objects.equals(normalizeOptional(menu.getMenuHtml()), normalizeOptional(source.getMenuHtml()))) {
            return toResponse(menu);
        }
        // Restore only HTML; menu name, route, visibility and structure stay intact.
        menu.setMenuHtml(source.getMenuHtml());
        menu.setHtmlRevisionNo(MenuHtmlHistoryService.revision(menu) + 1);
        historyService.record(menu, "RESTORE", source.getSeq(), changeMemo, adminSeq);
        menuSettingsRepository.updateHtml(menu);
        return toResponse(menu);
    }

    @Transactional
    public void delete(Long conferenceSeq, Long seq) {
        MenuSettings menu = menuSettingsRepository.findBySeqForUpdate(conferenceSeq, seq);
        if (menu == null) {
            throw new IllegalArgumentException("존재하지 않는 메뉴입니다.");
        }

        if (menuSettingsRepository.countChildren(conferenceSeq, menu.getMenuScope(), menu.getMenuKey()) > 0) {
            throw new IllegalArgumentException("하위 메뉴가 있는 메뉴는 삭제할 수 없습니다.");
        }

        if (historyService.hasHistory(seq) || (translationService != null && translationService.hasHistory(seq))) {
            throw new IllegalArgumentException("HTML 이력이 있는 메뉴는 삭제할 수 없습니다. 사용 여부를 해제해 주세요.");
        }
        if (translationService != null) translationService.deleteForMenu(seq);
        menuSettingsRepository.deleteBySeq(conferenceSeq, seq);
    }

    @Transactional
    public List<MenuSettingsResponse> reorder(Long conferenceSeq, List<MenuReorderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("정렬할 메뉴가 없습니다.");
        }

        List<MenuSettings> menus = menuSettingsRepository.findAll(conferenceSeq);
        Map<Long, MenuSettings> menuBySeq = new LinkedHashMap<>();
        Map<String, MenuSettings> menuByKey = new LinkedHashMap<>();
        for (MenuSettings menu : menus) {
            menuBySeq.put(menu.getSeq(), menu);
            menuByKey.put(nodeKey(menu.getMenuScope(), menu.getMenuKey()), menu);
        }

        Map<Long, String> requestedParentKeys = new HashMap<>();
        Map<Long, Integer> requestedSortOrders = new HashMap<>();
        Set<Long> visitedSeqs = new HashSet<>();

        for (MenuReorderItemRequest item : items) {
            if (item == null || item.getSeq() == null) {
                throw new IllegalArgumentException("정렬 요청에 잘못된 메뉴가 포함되어 있습니다.");
            }
            if (!visitedSeqs.add(item.getSeq())) {
                throw new IllegalArgumentException("중복된 메뉴 정렬 요청이 포함되어 있습니다.");
            }

            MenuSettings menu = menuBySeq.get(item.getSeq());
            if (menu == null) {
                throw new IllegalArgumentException("존재하지 않는 메뉴가 포함되어 있습니다.");
            }
            if ("root".equals(menu.getMenuKey())) {
                throw new IllegalArgumentException("루트 메뉴는 이동할 수 없습니다.");
            }

            String normalizedParentKey = normalizeOptional(item.getParentKey());
            Integer sortOrder = item.getSortOrder() != null ? item.getSortOrder() : menu.getSortOrder();
            requestedParentKeys.put(item.getSeq(), normalizedParentKey);
            requestedSortOrders.put(item.getSeq(), sortOrder);
        }

        for (MenuSettings menu : menus) {
            if ("root".equals(menu.getMenuKey())) {
                String nextParentKey = requestedParentKeys.getOrDefault(menu.getSeq(), normalizeOptional(menu.getParentKey()));
                if (nextParentKey != null) {
                    throw new IllegalArgumentException("루트 메뉴의 상위 메뉴는 변경할 수 없습니다.");
                }
                continue;
            }

            String nextParentKey = requestedParentKeys.getOrDefault(menu.getSeq(), normalizeOptional(menu.getParentKey()));
            if (nextParentKey == null) {
                continue;
            }

            MenuSettings parent = menuByKey.get(nodeKey(menu.getMenuScope(), nextParentKey));
            if (parent == null) {
                throw new IllegalArgumentException("상위 메뉴가 존재하지 않습니다.");
            }
            if (!menu.getMenuScope().equals(parent.getMenuScope())) {
                throw new IllegalArgumentException("다른 메뉴 영역으로 이동할 수 없습니다.");
            }
        }

        Map<Long, Integer> requestedDepths = new HashMap<>();
        for (MenuSettings menu : menus) {
            int depth = resolveDepth(menu, menuByKey, requestedParentKeys, new HashSet<>());
            validateMaxDepth(depth);
            requestedDepths.put(menu.getSeq(), depth);
        }

        for (Map.Entry<Long, String> entry : requestedParentKeys.entrySet()) {
            menuSettingsRepository.updateStructure(
                    conferenceSeq,
                    entry.getKey(),
                    entry.getValue(),
                    requestedDepths.get(entry.getKey()),
                    requestedSortOrders.get(entry.getKey())
            );
        }

        return getMenuTree(conferenceSeq);
    }

    private String normalizeScope(String menuScope) {
        String normalizedScope = normalizeRequired(menuScope, "메뉴 영역은 필수입니다.");
        if (!"admin".equals(normalizedScope) && !"user".equals(normalizedScope)) {
            throw new IllegalArgumentException("메뉴 영역은 admin 또는 user만 사용할 수 있습니다.");
        }
        return normalizedScope;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeLinkUrl(String linkUrl) {
        String normalized = normalizeOptional(linkUrl);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("링크 URL은 1000자 이하로 입력해주세요.");
        }

        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("링크 URL은 http:// 또는 https://로 시작하는 주소여야 합니다.");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("링크 URL 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeMenuType(String menuType) {
        String normalized = normalizeOptional(menuType);
        if (normalized == null) {
            return "folder";
        }
        if (!"folder".equals(normalized) && !"page".equals(normalized) && !"board".equals(normalized) && !"link".equals(normalized)) {
            throw new IllegalArgumentException("메뉴 유형이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String resolvePathType(String menuType) {
        return switch (menuType) {
            case "link" -> "external";
            case "folder" -> "segment";
            default -> "full";
        };
    }

    private String normalizeTargetType(String targetType) {
        String normalized = normalizeOptional(targetType);
        if (normalized == null) {
            return "self";
        }
        if (!"self".equals(normalized) && !"blank".equals(normalized)) {
            throw new IllegalArgumentException("링크 타겟이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeRoutePath(
            String menuScope,
            String pathType,
            String menuPath,
            String routePath,
            String linkUrl
    ) {
        if ("external".equals(pathType)) {
            if (linkUrl == null) {
                throw new IllegalArgumentException("외부 링크 경로에는 링크 URL이 필요합니다.");
            }
            return linkUrl;
        }

        String normalizedRoutePath = normalizeOptional(routePath);
        if (normalizedRoutePath == null && "user".equals(menuScope)) {
            normalizedRoutePath = menuPath != null ? menuPath : null;
        }
        if (normalizedRoutePath == null) {
            return null;
        }

        if (!normalizedRoutePath.startsWith("/")) {
            normalizedRoutePath = "/" + normalizedRoutePath;
        }

        if ("user".equals(menuScope) && !"/".equals(normalizedRoutePath)
                && !normalizedRoutePath.matches("/[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("사용자 페이지 경로는 /page-name 형식으로 입력해 주세요. 학회와 언어 경로는 자동으로 붙습니다.");
        }
        if ("user".equals(menuScope) && (normalizedRoutePath.matches("/[a-z]{2}(?:-[A-Za-z0-9]{2,8})*")
                || Set.of("/admin", "/api", "/public", "/assets", "/vendor", "/commoncode", "/error", "/actuator", "/webjars").contains(normalizedRoutePath)))
            throw new IllegalArgumentException("시스템 예약 경로는 메뉴 주소로 사용할 수 없습니다.");
        return normalizedRoutePath;
    }

    private void validateMenuTypeDependencies(String menuType, Long boardSeq, String linkUrl) {
        if ("board".equals(menuType) && boardSeq == null) {
            throw new IllegalArgumentException("게시판 메뉴에는 게시판 ID가 필요합니다.");
        }
        if ("link".equals(menuType) && linkUrl == null) {
            throw new IllegalArgumentException("링크 메뉴에는 링크 URL이 필요합니다.");
        }
    }

    private void validateRoutePathDuplicate(Long conferenceSeq, String menuScope, String routePath, Long currentSeq) {
        if (routePath == null) {
            return;
        }

        MenuSettings existing = menuSettingsRepository.findByScopeAndRoutePath(conferenceSeq, menuScope, routePath);
        if (existing != null && (currentSeq == null || !existing.getSeq().equals(currentSeq))) {
            throw new IllegalArgumentException("이미 사용 중인 URL 경로입니다.");
        }
    }

    private void validateUsePeriod(LocalDate useStartDate, LocalDate useEndDate) {
        if (useStartDate != null && useEndDate != null && useEndDate.isBefore(useStartDate)) {
            throw new IllegalArgumentException("사용 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private int resolveStoredDepth(MenuSettings parent) {
        if (parent == null) {
            return 0;
        }
        return (parent.getDepth() != null ? parent.getDepth() : 0) + 1;
    }

    private void validateMaxDepth(int depth) {
        if (depth > 3) {
            throw new IllegalArgumentException("메뉴는 최대 3단계까지만 사용할 수 있습니다.");
        }
    }

    private String nodeKey(String menuScope, String menuKey) {
        return menuScope + ":" + menuKey;
    }

    private int resolveDepth(
            MenuSettings menu,
            Map<String, MenuSettings> menuByKey,
            Map<Long, String> requestedParentKeys,
            Set<Long> lineage
    ) {
        if (!lineage.add(menu.getSeq())) {
            throw new IllegalArgumentException("메뉴 순환 참조는 허용되지 않습니다.");
        }

        try {
            if ("root".equals(menu.getMenuKey())) {
                return 0;
            }

            String parentKey = requestedParentKeys.getOrDefault(menu.getSeq(), normalizeOptional(menu.getParentKey()));
            if (parentKey == null) {
                return 0;
            }

            MenuSettings parent = menuByKey.get(nodeKey(menu.getMenuScope(), parentKey));
            if (parent == null) {
                throw new IllegalArgumentException("상위 메뉴가 존재하지 않습니다.");
            }

            return resolveDepth(parent, menuByKey, requestedParentKeys, lineage) + 1;
        } finally {
            lineage.remove(menu.getSeq());
        }
    }

    private MenuSettingsResponse toResponse(MenuSettings menu) {
        return MenuSettingsResponse.builder()
                .seq(menu.getSeq())
                .languageCode(menu.getLanguageCode())
                .translationReady(menu.getTranslationReady())
                .menuScope(menu.getMenuScope())
                .menuKey(menu.getMenuKey())
                .parentKey(menu.getParentKey())
                .menuName(menu.getMenuName())
                .menuPath(menu.getMenuPath())
                .menuType(menu.getMenuType())
                .pathType(menu.getPathType())
                .routePath(menu.getRoutePath())
                .depth(menu.getDepth())
                .boardSeq(menu.getBoardSeq())
                .linkUrl(menu.getLinkUrl())
                .targetType(menu.getTargetType())
                .authRequired(menu.getAuthRequired())
                .navigationVisible(menu.getNavigationVisible())
                .menuHtml(menu.getMenuHtml())
                .htmlRevisionNo(MenuHtmlHistoryService.revision(menu))
                .sortOrder(menu.getSortOrder())
                .useStartDate(menu.getUseStartDate())
                .useEndDate(menu.getUseEndDate())
                .enabled(menu.getEnabled())
                .createdAt(menu.getCreatedAt())
                .updatedAt(menu.getUpdatedAt())
                .build();
    }
}
