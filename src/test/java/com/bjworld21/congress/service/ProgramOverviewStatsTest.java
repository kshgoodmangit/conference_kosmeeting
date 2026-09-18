package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.ProgramManagementResponse;
import com.bjworld21.congress.entity.ProgramDay;
import com.bjworld21.congress.entity.ProgramItem;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramOverviewStatsTest {
    private static final String HTML = "<h2>Overview</h2><ul class='program-overview-stats un-style co-blue'>"
            + "<li><strong>08</strong><span>Session Tracks</span></li></ul><p>Editable introduction</p>";

    @Test
    void countsVisibleTopLevelScientificSessionsAndLecturesWithoutCountingPostersOrChildren() {
        var program = ProgramManagementResponse.builder()
                .days(List.of(day(1L, true), day(2L, false)))
                .items(List.of(
                        item(1L, null, "SESSION", "Session 1 — Development", true),
                        item(1L, null, "SESSION", "Session 2 — Biology", true),
                        item(1L, null, "SESSION", "Flash Talks", true),
                        item(1L, null, "SESSION", "Poster Session I", true),
                        item(1L, 10L, "SESSION", "Session 3", true),
                        item(1L, null, "SESSION", "Session 4", false),
                        item(2L, null, "SESSION", "Session 5", true),
                        item(1L, null, "PLENARY", "Plenary Lecture 1", true),
                        item(1L, null, "PLENARY", "Presidential Lecture", true),
                        item(2L, null, "PLENARY", "Hidden lecture", true)))
                .build();
        var result = Jsoup.parseBodyFragment(ProgramOverviewStats.render(HTML, program));
        assertThat(result.select(".program-overview-stats li").eachText()).containsExactly(
                "01 Conference Days", "02 Scientific Sessions", "02 Plenary & Presidential Lectures");
        assertThat(result.select("h2").text()).isEqualTo("Overview");
        assertThat(result.select("p").text()).isEqualTo("Editable introduction");
        assertThat(result.selectFirst("ul").classNames()).contains("un-style", "co-blue");
    }

    @Test
    void emptyScheduleReplacesStaleCmsNumbersWithZero() {
        var program = ProgramManagementResponse.builder().days(List.of()).items(List.of()).build();
        var result = Jsoup.parseBodyFragment(ProgramOverviewStats.render(HTML, program));
        assertThat(result.select("strong").eachText()).containsExactly("00", "00", "00");
        assertThat(ProgramOverviewStats.render("<p>No statistics block</p>", program))
                .isEqualTo("<p>No statistics block</p>");
    }

    @Test
    void localizesGeneratedStatisticsWithoutTranslatingTheEditorsContent() {
        var program = ProgramManagementResponse.builder().days(List.of(day(1L, true))).items(List.of()).build();
        var result = Jsoup.parseBodyFragment(ProgramOverviewStats.render(HTML, program, "ko"));
        assertThat(result.select(".program-overview-stats li").eachText()).containsExactly(
                "01 학회 일수", "00 학술 세션", "00 기조 및 회장 강연");
        assertThat(result.select("p").text()).isEqualTo("Editable introduction");
    }

    private ProgramDay day(Long seq, boolean enabled) {
        return ProgramDay.builder().seq(seq).enabled(enabled).build();
    }

    private ProgramItem item(Long day, Long parent, String type, String title, boolean enabled) {
        return ProgramItem.builder().programDaySeq(day).parentSeq(parent)
                .itemType(type).title(title).enabled(enabled).build();
    }
}
