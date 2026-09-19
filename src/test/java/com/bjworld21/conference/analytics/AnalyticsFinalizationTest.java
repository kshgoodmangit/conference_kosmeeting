package com.bjworld21.conference.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsFinalizationTest {
    private final LocalDate today=LocalDate.of(2026,9,14);
    private final Map<String,Object> current=Map.of("conferenceSeq",1L,"statDate",today);
    private final Map<String,Object> yesterday=Map.of("conferenceSeq",1L,"statDate",today.minusDays(1));

    private AnalyticsRepository repository(List<Map<String,Object>> pending) {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT DISTINCT"),any(LocalDateTime.class),any(LocalDateTime.class))).thenReturn(List.of(current));
        when(jdbc.queryForList(contains("updatedAt<TIMESTAMPADD"),eq(today))).thenReturn(pending);
        return new AnalyticsRepository(jdbc);
    }
    @Test void beforeHalfPastMidnightOnlyTodayIsPeriodic() {
        assertEquals(List.of(current),repository(List.of(yesterday)).recentDays(today.atTime(0,29,59)));
    }
    @Test void yesterdayIsFinalizedAtHalfPastMidnight() {
        assertEquals(List.of(current,yesterday),repository(List.of(yesterday)).recentDays(today.atTime(0,30)));
    }
    @Test void completedYesterdayIsNotQueuedAgain() {
        assertEquals(List.of(current),repository(List.of()).recentDays(today.atTime(12,0)));
    }
    @Test void missedFinalizationIsRecoveredAfterSeveralDaysOffline() {
        Map<String,Object> older=Map.of("conferenceSeq",2L,"statDate",today.minusDays(3));
        assertEquals(List.of(current,older),repository(List.of(older)).recentDays(today.atTime(0,10)));
    }
}
