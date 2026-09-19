package com.bjworld21.conference.analytics;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsDashboardQueryTest {
    private final NamedParameterJdbcTemplate named=mock(NamedParameterJdbcTemplate.class);
    private final AnalyticsRepository repository=new AnalyticsRepository(mock(JdbcTemplate.class));

    AnalyticsDashboardQueryTest() {
        ReflectionTestUtils.setField(repository,"named",named);
        when(named.queryForMap(anyString(),anyMap())).thenReturn(Map.of("visitors",10));
        when(named.queryForObject(anyString(),anyMap(),eq(Long.class))).thenReturn(3L);
    }

    @Test void allTimeBounceAllowsNewIndexWithoutChangingSessionRules() {
        assertEquals(3L,repository.allTimeTotals(7,true).get("bouncedSessions"));
        var sql=ArgumentCaptor.forClass(String.class);
        verify(named).queryForObject(sql.capture(),argThat((Map<String,?> p)->p.get("conference").equals(7L) && !p.containsKey("startDate")),eq(Long.class));
        assertFalse(sql.getValue().contains("FORCE INDEX"));
        assertFalse(sql.getValue().contains("statDate"));
        assertTrue(sql.getValue().contains("GROUP BY sessionIdHash"));
        assertTrue(sql.getValue().contains("HAVING SUM(pageViewCount)=1 AND SUM(totalDurationSeconds)<10"));
        assertTrue(sql.getValue().contains("MAX(lastOccurredAt)<:inactive"));
    }

    @Test void boundedBounceRetainsDateIndexAndRange() {
        var start=LocalDate.of(2026,8,16);var end=LocalDate.of(2026,9,14);
        repository.totals(7,start,end);
        var sql=ArgumentCaptor.forClass(String.class);
        verify(named).queryForObject(sql.capture(),argThat((Map<String,?> p)->p.get("startDate").equals(start) && p.get("endDate").equals(end)),eq(Long.class));
        assertTrue(sql.getValue().contains("FORCE INDEX (uk_analytics_daily_fact)"));
        assertTrue(sql.getValue().contains("statDate>=:startDate AND statDate<=:endDate"));
    }
}
