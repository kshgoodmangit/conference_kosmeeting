package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.AdminDashboardResponse;
import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.dto.OperationalDashboardResponse;
import com.bjworld21.conference.dto.ShowcaseDashboardResponse;
import com.bjworld21.conference.repository.AdminDashboardRepository;
import com.bjworld21.conference.repository.OperationalDashboardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShowcaseDashboardServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ConferenceSettingsService conferences = mock(ConferenceSettingsService.class);
    private final OperationalDashboardRepository operations = mock(OperationalDashboardRepository.class);
    private final AdminDashboardRepository dashboard = mock(AdminDashboardRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-19T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final ShowcaseDashboardService service = new ShowcaseDashboardService(jdbc, conferences, operations, dashboard, clock);

    @Test
    void selectedPastConferenceUsesItsDatesAndKeepsTodayMetricsSeparate() {
        when(conferences.getSettings(5L)).thenReturn(ConferenceSettingsResponse.builder()
                .seq(5L).eventName("135").eventStartDate(LocalDate.of(2026, 3, 3)).eventEndDate(LocalDate.of(2026, 3, 6)).build());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(5L)))
                .thenReturn(new ShowcaseDashboardResponse.Registration(100, 20));
        var historical = new OperationalDashboardResponse.Daily();
        historical.setDate(LocalDate.of(2026, 3, 2)); historical.setRegistered(12);
        var start = LocalDateTime.of(2026, 2, 24, 0, 0);
        var end = LocalDateTime.of(2026, 3, 3, 0, 0);
        when(operations.registrationDays(5L, start, end)).thenReturn(List.of(historical));
        var krw = new OperationalDashboardResponse.Amount(); krw.setCurrency("KRW"); krw.setPaid(new BigDecimal("120000"));
        var usd = new OperationalDashboardResponse.Amount(); usd.setCurrency("USD"); usd.setPaid(new BigDecimal("250.50"));
        when(operations.registrationAmounts(eq(5L), any(), any())).thenReturn(List.of(krw, usd));
        when(dashboard.findSponsorshipSummary(5L)).thenReturn(AdminDashboardResponse.SponsorshipSummary.builder().build());

        var result = service.getDashboard(5L);

        assertThat(result.conferenceSeq()).isEqualTo(5L);
        assertThat(result.event().getEventName()).isEqualTo("135");
        assertThat(result.trendStartDate()).isEqualTo("2026-02-24");
        assertThat(result.trendEndDate()).isEqualTo("2026-03-02");
        assertThat(result.trends()).hasSize(7);
        assertThat(result.trends().get(0).getRegistered()).isZero();
        assertThat(result.trends().get(6).getRegistered()).isEqualTo(12);
        assertThat(result.todayRegistrationCount()).isZero();
        assertThat(result.amounts()).extracting(OperationalDashboardResponse.Amount::getPaid)
                .containsExactly(new BigDecimal("120000"), new BigDecimal("250.50"));
        verify(operations).abstractDays(5L, start, end);
        verify(operations).abstractDays(5L, LocalDate.of(2026,9,19).atStartOfDay(), LocalDate.of(2026,9,20).atStartOfDay());
        verify(operations).abstractFields(5L, LocalDateTime.of(2026,9,19,10,0), AbstractDecisionService.REQUIRED_REVIEWER_COUNT);
        verify(conferences, never()).getSettings();
    }

    @Test
    void missingConferenceDoesNotFallBackToLatestOrQueryOtherTables() {
        when(conferences.getSettings(999L)).thenReturn(ConferenceSettingsResponse.builder().build());
        assertThatThrownBy(() -> service.getDashboard(999L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc, operations, dashboard);
    }

    @Test
    void currentAndOngoingConferencesUseTodayWhilePastConferencesUseTheDayBeforeOpening() {
        var today = LocalDate.of(2026,9,19);
        assertThat(ShowcaseDashboardService.trendEnd(today, LocalDate.of(2027,5,9), LocalDate.of(2027,5,12))).isEqualTo(today);
        assertThat(ShowcaseDashboardService.trendEnd(today, today.minusDays(2), today)).isEqualTo(today);
        assertThat(ShowcaseDashboardService.trendEnd(today, LocalDate.of(2025,10,3), LocalDate.of(2025,10,6))).isEqualTo("2025-10-02");
        assertThat(ShowcaseDashboardService.trendEnd(today, null, null)).isEqualTo(today);
    }
}
