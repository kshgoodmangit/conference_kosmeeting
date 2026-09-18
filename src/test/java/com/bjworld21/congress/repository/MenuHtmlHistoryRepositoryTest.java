package com.bjworld21.congress.repository;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MenuHtmlHistoryRepositoryTest {
    @Test void queriesAreScopedAndRestoreDoesNotWriteOtherSettings() {
        var config = new Configuration();
        config.addMapper(MenuHtmlHistoryRepository.class);
        config.addMapper(MenuSettingsRepository.class);
        String history = MenuHtmlHistoryRepository.class.getName() + ".";
        String menu = MenuSettingsRepository.class.getName() + ".";
        var parameters = Map.of("seq", 2L, "menuSeq", 3L, "conferenceSeq", 1L, "limit", 20, "offset", 0);
        String page = config.getMappedStatement(history + "findPage").getBoundSql(parameters).getSql();
        assertThat(page).contains("WHERE menuSeq = ?", "ORDER BY revisionNo DESC LIMIT ? OFFSET ?");
        assertThat(page.substring(0, page.indexOf("FROM"))).doesNotContain("menuHtml");
        String detail = config.getMappedStatement(history + "findBySeq").getBoundSql(parameters).getSql();
        assertThat(detail).contains("menuSeq = ? AND seq = ?");
        String lock = config.getMappedStatement(menu + "findBySeqForUpdate").getBoundSql(parameters).getSql();
        assertThat(lock).contains("FOR UPDATE", "conferenceSeq = ?", "menuScope = 'admin'", "conferenceSeq IS NULL");
        String restore = config.getMappedStatement(menu + "updateHtml").getBoundSql(parameters).getSql();
        assertThat(restore).contains("menuHtml = ?", "htmlRevisionNo = ?").doesNotContain("menuName =", "routePath =", "enabled =", "sortOrder =");
    }
}
