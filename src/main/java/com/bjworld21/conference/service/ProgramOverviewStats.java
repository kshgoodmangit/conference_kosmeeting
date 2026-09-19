package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.ProgramManagementResponse;
import com.bjworld21.conference.entity.ProgramDay;
import com.bjworld21.conference.entity.ProgramItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Replaces only the CMS overview statistics, preserving the editable surrounding content. */
public final class ProgramOverviewStats {
    // The current schedule uses SESSION for numbered scientific sessions, flash talks and posters.
    // Only the numbered "Session N" entries represent the scientific tracks advertised here.
    private static final Pattern SCIENTIFIC_SESSION = Pattern.compile("^Session\\s+\\d+\\b", Pattern.CASE_INSENSITIVE);

    private ProgramOverviewStats() {
    }

    public static String render(String sanitizedHtml, ProgramManagementResponse program) {
        return render(sanitizedHtml, program, "en");
    }

    public static String render(String sanitizedHtml, ProgramManagementResponse program, String language) {
        boolean korean = "ko".equals(language);
        var document = Jsoup.parseBodyFragment(sanitizedHtml);
        var containers = document.select(".program-overview-stats");
        if (containers.isEmpty()) {
            return sanitizedHtml;
        }
        List<ProgramDay> days = program.getDays();
        Set<Long> visibleDays = days.stream()
                .filter(day -> !Boolean.FALSE.equals(day.getEnabled()))
                .map(ProgramDay::getSeq).collect(Collectors.toSet());
        List<ProgramItem> items = program.getItems().stream()
                .filter(item -> visibleDays.contains(item.getProgramDaySeq()))
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()) && item.getParentSeq() == null)
                .toList();
        long sessions = items.stream()
                .filter(item -> "SESSION".equals(item.getItemType()))
                .filter(item -> item.getTitle() != null && SCIENTIFIC_SESSION.matcher(item.getTitle().strip()).find())
                .count();
        long lectures = items.stream().filter(item -> "PLENARY".equals(item.getItemType())).count();
        for (Element container : containers) {
            container.empty();
            addStat(container, visibleDays.size(), korean ? "학회 일수" : "Conference Days");
            addStat(container, sessions, korean ? "학술 세션" : "Scientific Sessions");
            addStat(container, lectures, korean ? "기조 및 회장 강연" : "Plenary & Presidential Lectures");
        }
        return document.body().html();
    }

    private static void addStat(Element container, long count, String label) {
        Element item = container.appendElement("li");
        item.appendElement("strong").text(String.format(Locale.ROOT, "%02d", count));
        item.appendText(" ");
        item.appendElement("span").text(label);
    }
}
