package com.bjworld21.conference.analytics;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsConferenceComparisonTest {
    private final AnalyticsRepository repository=mock(AnalyticsRepository.class);
    private final LocalDate end=LocalDate.of(2026,9,14);
    private final LocalDate start=end.minusDays(29);

    @Test void firstConferenceHasNoComparisonAndDoesNotReadPreviousPeriod() {
        var totals=Map.<String,Object>of("visitors",12,"sessions",15,"pageViews",30,"totalDurationSeconds",900,"bouncedSessions",2);
        when(repository.allTimeTotals(1,true)).thenReturn(totals);
        var result=new AnalyticsSnapshotReader(repository).overview(1,start,end,true);
        assertEquals(totals,result.get("totals"));
        assertTrue(result.containsKey("previous"));assertNull(result.get("previous"));
        assertNull(result.get("previousConference"));
        assertNull(result.get("startDate"));assertNull(result.get("endDate"));
        assertEquals(List.of(),result.get("trend"));
        verify(repository).allTimeTotals(1,true);
        verify(repository,never()).allTimeTotals(anyLong(),eq(false));
        verify(repository,never()).totals(anyLong(),any(),any());
    }

    @Test void emptyPreviousConferenceIsNotSkippedForAnOlderConference() {
        var previous=new AnalyticsRepository.PreviousConference(4,"Empty previous event");
        var zero=Map.<String,Object>of("visitors",0,"sessions",0,"pageViews",0,"totalDurationSeconds",0);
        when(repository.previousConference(8)).thenReturn(Optional.of(previous));
        when(repository.allTimeTotals(4,false)).thenReturn(zero);
        var result=new AnalyticsSnapshotReader(repository).overview(8,start,end,true);
        assertEquals(previous,result.get("previousConference"));assertEquals(zero,result.get("previous"));
        verify(repository,never()).previousConference(4);
        verify(repository,never()).allTimeTotals(4,true);
    }

    @Test void previousConferenceLookupUsesExistingLowerSequenceIncludingGaps() {
        var jdbc=mock(JdbcTemplate.class);
        var actual=new AnalyticsRepository(jdbc);
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<AnalyticsRepository.PreviousConference>>any(),eq(8L)))
                .thenReturn(List.of(new AnalyticsRepository.PreviousConference(4,"Previous event")));
        assertEquals(4,actual.previousConference(8).orElseThrow().conferenceSeq());
        var sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(),org.mockito.ArgumentMatchers.<RowMapper<AnalyticsRepository.PreviousConference>>any(),eq(8L));
        assertTrue(sql.getValue().contains("WHERE seq<? ORDER BY seq DESC LIMIT 1"));
        assertFalse(sql.getValue().contains("analytics_daily_facts"));
    }

    @Test void pendingPreviousAggregationCannotBeShownAsCompleteComparison() {
        when(repository.conferenceExists(8)).thenReturn(true);
        when(repository.pendingDays()).thenReturn(List.of(Map.of("conferenceSeq",4,"statDate",start.toString())));
        doThrow(new IllegalStateException("aggregation unavailable")).when(repository).rebuildDay(4,start);
        when(repository.previousConference(8)).thenReturn(Optional.of(new AnalyticsRepository.PreviousConference(4,"Previous")));
        var service=new AnalyticsService(repository);
        try {
            service.aggregateDirtyDays();
            assertThrows(AnalyticsService.AggregationPendingException.class,()->service.dashboard(8,null,null));
        } finally {service.close();}
    }
}
