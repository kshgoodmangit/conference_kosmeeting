package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.MenuSettingsResponse;
import com.bjworld21.conference.entity.MenuSettings;
import com.bjworld21.conference.repository.MenuSettingsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuSettingsServiceTest {

    @Test
    void publicAndAdminMenusUseStoredRoutePathsWithoutMenuPathFallback() {
        var repository = mock(MenuSettingsRepository.class);
        var service = new MenuSettingsService(repository, mock(MenuHtmlHistoryService.class));
        var welcome = menu(2L, "welcome-message", null, 1);
        welcome.setMenuPath("welcome-message");
        welcome.setRoutePath("/arbitrary-old-folder/welcome-message");
        var abstracts = menu(3L, "mypage-abstract", null, 2);
        abstracts.setMenuPath("mypage-abstract");
        abstracts.setRoutePath("/mypage/abstract");
        var canonical = menu(4L, "custom-key", null, 3);
        canonical.setMenuPath("stale-page-name");
        canonical.setRoutePath("/custom-page");
        var external = menu(5L, "external", null, 4);
        external.setMenuType("link");
        external.setMenuPath("external");
        external.setRoutePath("https://example.org/folder/page");
        var rows = List.of(welcome, abstracts, canonical, external);
        when(repository.findActiveByScope(eq(1L), eq("user"), isA(LocalDate.class))).thenReturn(rows);
        when(repository.findAll(1L)).thenReturn(rows);
        var translations = mock(MenuTranslationService.class);
        when(translations.localize(1L, rows, "en")).thenReturn(rows);
        service.setTranslationService(translations);

        for (var tree : List.of(service.getActiveUserMenuTree(1L), service.getActiveUserMenuTree(1L, "en"))) {
            assertThat(tree).extracting(MenuSettingsResponse::getRoutePath).containsExactly(
                    "/arbitrary-old-folder/welcome-message", "/mypage/abstract", "/custom-page", "https://example.org/folder/page");
        }
        assertThat(service.getMenuTree(1L)).extracting(MenuSettingsResponse::getRoutePath).containsExactly(
                "/arbitrary-old-folder/welcome-message", "/mypage/abstract", "/custom-page", "https://example.org/folder/page");
        verify(repository, never()).update(any());
    }

    @Test
    void refusesAmbiguousCanonicalMenuRoutesInsteadOfSelectingTheWrongMenu() {
        var repository = mock(MenuSettingsRepository.class);
        var service = new MenuSettingsService(repository, mock(MenuHtmlHistoryService.class));
        var first = menu(1L, "first", null, 1);
        first.setMenuPath("same-page");
        first.setRoutePath("/same-page");
        var second = menu(2L, "second", null, 2);
        second.setRoutePath("/same-page");
        when(repository.findActiveByScope(eq(1L), eq("user"), isA(LocalDate.class))).thenReturn(List.of(first, second));
        assertThatThrownBy(() -> service.getActiveUserMenuTree(1L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    void activeTreeDoesNotPromoteDescendantsOfDisabledOrExpiredAncestor() {
        var repository=mock(MenuSettingsRepository.class);
        var service=new MenuSettingsService(repository,mock(MenuHtmlHistoryService.class));
        // The SQL date/enabled filter removed protected-folder; its still-active
        // child and grandchild must not become accessible standalone roots.
        when(repository.findActiveByScope(eq(1L),eq("user"),isA(LocalDate.class))).thenReturn(List.of(
                menu(1L,"root",null,0),
                menu(3L,"private-board","protected-folder",10),
                menu(4L,"private-detail","private-board",10)));
        assertThat(service.getActiveUserMenuTree(1L)).isEmpty();
    }

    @Test
    void unwrappedRootAuthenticationRemainsOnEveryVisibleBranch() {
        var repository=mock(MenuSettingsRepository.class);
        var service=new MenuSettingsService(repository,mock(MenuHtmlHistoryService.class));
        var root=menu(1L,"root",null,0);
        root.setAuthRequired(true);
        when(repository.findActiveByScope(eq(1L),eq("user"),isA(LocalDate.class))).thenReturn(List.of(
                root,menu(2L,"program","root",10),menu(3L,"speakers","program",10),menu(4L,"notice","root",20)));
        var tree=service.getActiveUserMenuTree(1L);
        assertThat(tree).extracting(MenuSettingsResponse::getAuthRequired).containsExactly(true,true);
        assertThat(tree.get(0).getChildren()).extracting(MenuSettingsResponse::getMenuKey).containsExactly("speakers");
        assertThat(tree).extracting(MenuSettingsResponse::getMenuKey).doesNotContain("root");
    }

    @Test
    void administrativeTreeRetainsOrphansForRepair() {
        var repository=mock(MenuSettingsRepository.class);
        var service=new MenuSettingsService(repository,mock(MenuHtmlHistoryService.class));
        when(repository.findAll(1L)).thenReturn(List.of(menu(3L,"orphan","missing-parent",10)));
        assertThat(service.getMenuTree(1L)).extracting(MenuSettingsResponse::getMenuKey).containsExactly("orphan");
    }

    @Test
    void userPagesRejectLanguageSlugsAndReservedSystemRoutes() {
        var repository=mock(MenuSettingsRepository.class);
        var service=new MenuSettingsService(repository,mock(MenuHtmlHistoryService.class));
        when(repository.findBySeqForUpdate(1L,2L)).thenReturn(menu(2L,"welcome",null,0));
        for (String path : List.of("/ja", "/fr", "/zh-hans", "/vendor", "/commoncode", "/program/welcome")) {
            assertThatThrownBy(() -> service.update(1L,2L,"Welcome","welcome","page","full",path,
                    null,null,"self",false,true,null,null,null,true,9L,null,false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(repository,never()).update(any());
    }

    @Test
    void buildsActiveUserTreeAndUnwrapsConfiguredRoot() {
        MenuSettingsRepository repository = mock(MenuSettingsRepository.class);
        MenuSettingsService service = new MenuSettingsService(repository, mock(MenuHtmlHistoryService.class));
        when(repository.findActiveByScope(eq(1L), eq("user"), isA(LocalDate.class))).thenReturn(List.of(
                menu(1L, "root", null, 0),
                menu(2L, "program", "root", 10),
                menu(3L, "scientific-program", "program", 10)
        ));

        List<MenuSettingsResponse> menus = service.getActiveUserMenuTree(1L);

        assertThat(menus).extracting(MenuSettingsResponse::getMenuKey).containsExactly("program");
        assertThat(menus.get(0).getChildren())
                .extracting(MenuSettingsResponse::getMenuKey)
                .containsExactly("scientific-program");
        verify(repository).findActiveByScope(eq(1L), eq("user"), isA(LocalDate.class));
    }

    @Test
    void createAcceptsHttpsExternalLink() {
        MenuSettingsRepository repository = mock(MenuSettingsRepository.class);
        MenuSettingsService service = new MenuSettingsService(repository, mock(MenuHtmlHistoryService.class));

        MenuSettingsResponse response = createExternalLink(service, "  https://conference.example/program?q=1  ");

        assertThat(response.getLinkUrl()).isEqualTo("https://conference.example/program?q=1");
        assertThat(response.getRoutePath()).isEqualTo("https://conference.example/program?q=1");
        assertThat(response.getPathType()).isEqualTo("external");
        verify(repository).insert(any(MenuSettings.class));
    }

    @Test
    void createRejectsExecutableAndProtocolRelativeExternalLinks() {
        MenuSettingsRepository repository = mock(MenuSettingsRepository.class);
        MenuSettingsService service = new MenuSettingsService(repository, mock(MenuHtmlHistoryService.class));

        assertThatThrownBy(() -> createExternalLink(service, "javascript:alert(document.domain)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("링크 URL");
        assertThatThrownBy(() -> createExternalLink(service, "data:text/html,<script>alert(1)</script>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("링크 URL");
        assertThatThrownBy(() -> createExternalLink(service, "//evil.example/path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("링크 URL");

        verify(repository, never()).insert(any(MenuSettings.class));
    }

    @Test
    void updateRejectsExecutableLink() {
        MenuSettingsRepository repository = mock(MenuSettingsRepository.class);
        MenuSettingsService service = new MenuSettingsService(repository, mock(MenuHtmlHistoryService.class));
        MenuSettings existing = menu(2L, "external-program", "root", 10);
        when(repository.findBySeqForUpdate(1L, 2L)).thenReturn(existing);

        assertThatThrownBy(() -> service.update(
                1L,
                2L,
                "External Program",
                "external-program",
                "link",
                "external",
                null,
                null,
                "javascript:alert(1)",
                "blank",
                false,
                true,
                null,
                null,
                null,
                true, 9L, null, true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http:// 또는 https://");

        verify(repository, never()).update(any(MenuSettings.class));
    }

    @Test
    void updatePageIgnoresStaleExternalPathType() {
        MenuSettingsRepository repository = mock(MenuSettingsRepository.class);
        MenuSettingsService service = new MenuSettingsService(repository, mock(MenuHtmlHistoryService.class));
        MenuSettings existing = menu(2L, "program", null, 10);
        existing.setMenuType("link");
        existing.setPathType("external");
        when(repository.findBySeqForUpdate(1L, 2L)).thenReturn(existing);

        MenuSettingsResponse response = service.update(
                1L, 2L, "Program", "program", "page", "external",
                "/program", null, null, "self", false, true,
                null, null, null, true, 9L, null, false
        );

        assertThat(response.getPathType()).isEqualTo("full");
        assertThat(response.getRoutePath()).isEqualTo("/program");
        verify(repository).update(existing);
    }

    private MenuSettingsResponse createExternalLink(MenuSettingsService service, String linkUrl) {
        return service.create(
                1L,
                "user",
                "external-program",
                null,
                "External Program",
                "external-program",
                "link",
                null,
                null,
                null,
                linkUrl,
                "blank",
                false,
                true,
                null,
                10,
                null,
                null,
                true, 9L, null
        );
    }

    private MenuSettings menu(Long seq, String menuKey, String parentKey, int sortOrder) {
        return MenuSettings.builder()
                .seq(seq)
                .menuScope("user")
                .menuKey(menuKey)
                .parentKey(parentKey)
                .menuName(menuKey)
                .menuType("page")
                .pathType("full")
                .routePath("/" + menuKey)
                .navigationVisible(true)
                .enabled(true)
                .sortOrder(sortOrder)
                .build();
    }
}
