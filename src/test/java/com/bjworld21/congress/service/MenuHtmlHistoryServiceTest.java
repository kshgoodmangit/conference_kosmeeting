package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.entity.AdminAccount;
import com.bjworld21.congress.entity.MenuHtmlHistory;
import com.bjworld21.congress.entity.MenuSettings;
import com.bjworld21.congress.repository.AdminAccountRepository;
import com.bjworld21.congress.repository.MenuHtmlHistoryRepository;
import com.bjworld21.congress.repository.MenuSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MenuHtmlHistoryServiceTest {
    private final MenuSettingsRepository menus = mock(MenuSettingsRepository.class);
    private final MenuHtmlHistoryRepository histories = mock(MenuHtmlHistoryRepository.class);
    private final AdminAccountRepository admins = mock(AdminAccountRepository.class);
    private final PersonalDataProperties properties = new PersonalDataProperties();
    private final MenuHtmlHistoryService historyService = new MenuHtmlHistoryService(histories, menus, admins, properties, new CmsHtmlSanitizer());
    private final MenuSettingsService service = new MenuSettingsService(menus, historyService);
    private MenuSettings menu;

    @BeforeEach void setUp() {
        properties.setDbEncString("test-only-key");
        when(admins.findBySeq(9L, "test-only-key")).thenReturn(AdminAccount.builder().seq(9L)
                .adminName("테스트 관리자").role("admin").status("active").build());
        menu = MenuSettings.builder().seq(2L).conferenceSeq(1L).menuScope("user")
                .menuKey("program").menuName("Program").menuPath("program").menuType("page")
                .pathType("full").routePath("/program").targetType("self").authRequired(false)
                .enabled(true).navigationVisible(true).sortOrder(7).menuHtml("<p>Before</p>")
                .htmlRevisionNo(1L).build();
        when(menus.findBySeqForUpdate(1L, 2L)).thenReturn(menu);
        when(menus.findBySeq(1L, 2L)).thenReturn(menu);
    }

    private void update(String html, boolean changed) {
        updateWith(service, html, changed);
    }

    private void updateWith(MenuSettingsService target, String html, boolean changed) {
        target.update(1L, 2L, "Renamed", "program", "page", "full", "/program", null, null,
                "self", false, true, html, null, null, true, 9L, "수정 메모", changed);
    }

    private MenuHtmlHistory recorded() {
        var capture = ArgumentCaptor.forClass(MenuHtmlHistory.class);
        verify(histories).insert(capture.capture());
        return capture.getValue();
    }

    @Test void initialCreationStoresV1AndServerDerivedActor() {
        doAnswer(call -> { ((MenuSettings) call.getArgument(0)).setSeq(5L); return null; }).when(menus).insert(any());
        var response = service.create(1L, "user", "new", null, "New", "new", "page", "full", "/new",
                null, null, "self", false, true, "<p>New</p>", 0, null, null, true, 9L, null);
        assertThat(response.getHtmlRevisionNo()).isEqualTo(1);
        assertThat(recorded()).satisfies(h -> {
            assertThat(h.getMenuSeq()).isEqualTo(5);
            assertThat(h.getOperationType()).isEqualTo("INITIAL");
            assertThat(h.getCreatedBy()).isEqualTo(9);
            assertThat(h.getCreatedByName()).isEqualTo("테스트 관리자");
        });
    }

    @Test void sameHtmlOrSettingsOnlyDoesNotCreateHistory() {
        update("  <p>Before</p>\n", true);
        assertThat(menu.getHtmlRevisionNo()).isEqualTo(1);
        assertThat(menu.getMenuName()).isEqualTo("Renamed");
        verify(histories, never()).insert(any());
        verify(menus).update(menu);
    }

    @Test void untouchedEditorDoesNotOverwriteHtmlWithFormattingOrStaleContent() {
        update("<p>Stale normalized editor data</p>\n", false);
        assertThat(menu.getMenuHtml()).isEqualTo("<p>Before</p>");
        verify(histories, never()).insert(any());
    }

    @Test void caseSensitiveChangeCreatesExactlyOneNewSnapshot() {
        update("<p>before</p>", true);
        assertThat(menu.getHtmlRevisionNo()).isEqualTo(2);
        assertThat(recorded()).satisfies(h -> {
            assertThat(h.getMenuHtml()).isEqualTo("<p>before</p>");
            assertThat(h.getOperationType()).isEqualTo("SAVE");
            assertThat(h.getRevisionNo()).isEqualTo(2);
        });
        update("<p>before</p>", true);
        verify(histories, times(1)).insert(any());
    }

    @Test void clearingHtmlIsRecordedButNullAndBlankAreEquivalent() {
        update(" ", true);
        assertThat(recorded().getMenuHtml()).isNull();
        update(null, true);
        verify(histories, times(1)).insert(any());
        assertThat(menu.getHtmlRevisionNo()).isEqualTo(2);
    }

    @Test void restoreAppendsVersionAndOnlyUpdatesHtml() {
        when(histories.findBySeq(2L, 50L)).thenReturn(MenuHtmlHistory.builder().seq(50L).menuHtml(" <h2>Original</h2> ").revisionNo(1L).build());
        menu.setHtmlRevisionNo(5L);
        var response = service.restoreHtml(1L, 2L, 50L, 9L, "복원 메모");
        assertThat(response.getHtmlRevisionNo()).isEqualTo(6);
        assertThat(response.getMenuName()).isEqualTo("Program");
        assertThat(response.getRoutePath()).isEqualTo("/program");
        assertThat(response.getSortOrder()).isEqualTo(7);
        assertThat(response.getMenuHtml()).isEqualTo(" <h2>Original</h2> ");
        assertThat(recorded()).satisfies(h -> {
            assertThat(h.getOperationType()).isEqualTo("RESTORE");
            assertThat(h.getRestoredFromSeq()).isEqualTo(50);
        });
        verify(menus).updateHtml(menu);
        verify(menus, never()).update(any());
    }

    @Test void restoringSameHtmlDoesNotWriteAnything() {
        when(histories.findBySeq(2L, 50L)).thenReturn(MenuHtmlHistory.builder().seq(50L).menuHtml("<p>Before</p>").build());
        service.restoreHtml(1L, 2L, 50L, 9L, null);
        verify(histories, never()).insert(any());
        verify(menus, never()).updateHtml(any());
    }

    @Test void rejectsOtherMenuHistoryAndOtherConference() {
        assertThatThrownBy(() -> service.restoreHtml(1L, 2L, 999L, 9L, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.restoreHtml(8L, 2L, 50L, 9L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> historyService.list(8L, 2L, 0, 20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> historyService.detail(8L, 2L, 50L)).isInstanceOf(ResponseStatusException.class);
        verify(histories, never()).insert(any());
        verify(histories, never()).findPage(any(), anyInt(), anyLong());
    }

    @Test void previewIsSanitizedButSourceRemainsIntact() {
        String html = "<p class='title'>Original</p><script>alert(1)</script>";
        when(histories.findBySeq(2L, 50L)).thenReturn(MenuHtmlHistory.builder().seq(50L).menuHtml(html).build());
        var detail = historyService.detail(1L, 2L, 50L);
        assertThat(detail.history().getMenuHtml()).isEqualTo(html);
        assertThat(detail.previewHtml()).doesNotContain("<script").contains("Original");
    }

    @Test void paginatesAndRejectsInvalidBounds() {
        when(histories.findPage(2L, 20, 20L)).thenReturn(List.of());
        when(histories.count(2L)).thenReturn(22L);
        assertThat(historyService.list(1L, 2L, 1, 20).total()).isEqualTo(22);
        verify(histories).findPage(2L, 20, 20L);
        assertThatThrownBy(() -> historyService.list(1L, 2L, -1, 20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> historyService.list(1L, 2L, 0, 101)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsOversizedMemoAndInactiveActor() {
        assertThatThrownBy(() -> service.restoreHtml(1L, 2L, 3L, 9L, "x".repeat(501))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> MenuHtmlHistoryService.normalizeMemo("x".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        when(admins.findBySeq(9L, "test-only-key")).thenReturn(null);
        assertThatThrownBy(() -> update("<p>Changed</p>", true)).isInstanceOf(ResponseStatusException.class);
        verify(histories, never()).insert(any());
        verify(menus, never()).update(any());
    }

    @Test void protectsMenusWithHistoryFromDeletion() {
        when(histories.count(2L)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(1L, 2L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이력");
        verify(menus, never()).deleteBySeq(any(), any());
    }

    @Test void currentHtmlWriteFailureRollsBackTheSaveTransaction() {
        var manager = mock(PlatformTransactionManager.class);
        var status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(service);
        factory.addAdvice(interceptor);
        var transactional = (MenuSettingsService) factory.getProxy();
        doThrow(new IllegalStateException("write failed")).when(menus).update(any());
        assertThatThrownBy(() -> updateWith(transactional, "<p>Changed</p>", true)).isInstanceOf(IllegalStateException.class);
        verify(histories).insert(any());
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }
}
