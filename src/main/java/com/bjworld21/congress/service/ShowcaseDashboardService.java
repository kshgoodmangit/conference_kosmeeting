package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.ShowcaseDashboardResponse;
import com.bjworld21.congress.dto.OperationalDashboardResponse.Daily;
import com.bjworld21.congress.repository.AdminDashboardRepository;
import com.bjworld21.congress.repository.OperationalDashboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class ShowcaseDashboardService {
    private final JdbcTemplate jdbc;
    private final ConferenceSettingsService conferences;
    private final OperationalDashboardRepository operations;
    private final AdminDashboardRepository dashboard;
    private final Clock clock;

    @Autowired
    public ShowcaseDashboardService(JdbcTemplate jdbc, ConferenceSettingsService conferences,
            OperationalDashboardRepository operations, AdminDashboardRepository dashboard) {
        this(jdbc, conferences, operations, dashboard, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    ShowcaseDashboardService(JdbcTemplate jdbc, ConferenceSettingsService conferences,
            OperationalDashboardRepository operations, AdminDashboardRepository dashboard, Clock clock) {
        this.jdbc = jdbc;
        this.conferences = conferences;
        this.operations = operations;
        this.dashboard = dashboard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ShowcaseDashboardResponse getDashboard(long conferenceSeq) {
        var event = conferences.getSettings(conferenceSeq);
        if (event.getSeq() == null) throw new IllegalArgumentException("학회를 찾을 수 없습니다.");
        var now = LocalDateTime.now(clock);
        var today = now.toLocalDate();
        var end = trendEnd(today, event.getEventStartDate(), event.getEventEndDate());
        var start = end.minusDays(6);
        var days = new LinkedHashMap<LocalDate, Daily>();
        start.datesUntil(end.plusDays(1)).forEach(date -> {
            var day = new Daily(); day.setDate(date); days.put(date, day);
        });
        for (var row : operations.registrationDays(conferenceSeq, start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
            days.get(row.getDate()).setRegistered(row.getRegistered());
        for (var row : operations.abstractDays(conferenceSeq, start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
            days.get(row.getDate()).setSubmitted(row.getSubmitted());

        var registration = jdbc.queryForObject("""
                SELECT COALESCE(SUM(paymentStatus = 'PAID'), 0) AS paidCount,
                    COALESCE(SUM(paymentStatus IN ('UNPAID', 'FAILED')), 0) AS unpaidCount
                FROM pre_registrations WHERE conferenceSeq = ? AND applicationStatus = 'SUBMITTED'
                """, (rs, row) -> new ShowcaseDashboardResponse.Registration(rs.getLong("paidCount"), rs.getLong("unpaidCount")), conferenceSeq);
        long memberCount = count("SELECT COUNT(*) FROM members WHERE conferenceSeq = ?", conferenceSeq);
        long todayMembers = count("SELECT COUNT(*) FROM members WHERE conferenceSeq = ? AND createdAt >= ? AND createdAt < ?",
                conferenceSeq, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long todayRegistrations = count("SELECT COUNT(*) FROM pre_registrations WHERE conferenceSeq = ? AND createdAt >= ? AND createdAt < ?",
                conferenceSeq, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long todayAbstracts = operations.abstractDays(conferenceSeq, today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream().mapToLong(Daily::getSubmitted).sum();
        var countries = jdbc.query("""
                SELECT COALESCE(c.isoAlpha2, 'ZZ') AS code,
                    COALESCE(NULLIF(c.countryNameKo, ''), c.countryName, NULLIF(TRIM(m.country), ''), '국가 미지정') AS name,
                    COUNT(*) AS participants
                FROM pre_registrations p
                JOIN members m ON m.seq = p.memberSeq AND m.conferenceSeq = p.conferenceSeq
                LEFT JOIN countries c ON c.seq = (
                    SELECT c2.seq FROM countries c2
                    WHERE c2.isoAlpha2 = TRIM(m.country) OR c2.isoAlpha3 = TRIM(m.country)
                        OR c2.countryName = TRIM(m.country) OR c2.countryNameKo = TRIM(m.country)
                    ORDER BY c2.seq LIMIT 1)
                WHERE p.conferenceSeq = ? AND p.applicationStatus = 'SUBMITTED'
                    AND p.paymentStatus IN ('PAID', 'UNPAID', 'FAILED')
                GROUP BY code, name ORDER BY participants DESC, name
                """, (rs, row) -> new ShowcaseDashboardResponse.Country(rs.getString("code"), rs.getString("name"), rs.getLong("participants")), conferenceSeq);
        var partners = jdbc.queryForList("""
                SELECT sponsorName FROM sponsors WHERE conferenceSeq = ? AND enabled = TRUE
                ORDER BY sortOrder, seq
                """, String.class, conferenceSeq);
        return new ShowcaseDashboardResponse(conferenceSeq, event, today, now, start, end,
                memberCount, todayMembers, todayRegistrations, todayAbstracts, registration,
                operations.registrationAmounts(conferenceSeq, today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                operations.abstractFields(conferenceSeq, now, AbstractDecisionService.REQUIRED_REVIEWER_COUNT),
                new ArrayList<>(days.values()), countries, partners, dashboard.findSponsorshipSummary(conferenceSeq));
    }

    static LocalDate trendEnd(LocalDate today, LocalDate eventStart, LocalDate eventEnd) {
        return eventStart != null && eventEnd != null && eventEnd.isBefore(today)
                ? eventStart.minusDays(1) : today;
    }

    private long count(String sql, Object... parameters) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, parameters));
    }
}
