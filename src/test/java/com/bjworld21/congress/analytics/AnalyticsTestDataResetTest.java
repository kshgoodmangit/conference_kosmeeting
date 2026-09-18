package com.bjworld21.congress.analytics;

import com.bjworld21.congress.config.LicenseProperties;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsTestDataResetTest {
    private final AnalyticsRepository repository=mock(AnalyticsRepository.class);
    private final AnalyticsService analytics=mock(AnalyticsService.class);
    private final AnalyticsCollector collector=mock(AnalyticsCollector.class);
    private final LocalDateTime now=LocalDateTime.of(2026,9,14,1,0);
    private final Clock clock=Clock.fixed(now.atZone(AnalyticsService.ZONE).toInstant(),AnalyticsService.ZONE);

    @Test void regenerationDeletesFirstAndProducesExactlySixtySmallerDaysOnEveryRun() {
        var resets=new AtomicLong();
        when(analytics.beginTestDataReset(7)).thenAnswer(call->{
            resets.incrementAndGet();
            return Map.of("deletedEvents",100L,"deletedFacts",50L);
        });
        when(collector.hash(anyString(),anyString())).thenAnswer(call->call.getArgument(0)+":"+call.getArgument(1));
        var views=new TreeMap<LocalDate,Integer>();
        var visitors=new HashSet<String>();var sessions=new HashSet<String>();
        var count=new AtomicLong();var duration=new AtomicLong();
        doAnswer(call->{
            assertEquals(1L, resets.get(), "Existing data must be deleted before inserting each new batch");
            List<Object[]> batch=call.getArgument(0);
            for(var event:batch) {
                assertEquals(7L,event[0]);
                var time=((Timestamp)event[18]).toLocalDateTime();
                assertFalse(time.toLocalDate().isBefore(now.toLocalDate().minusDays(59)));
                assertTrue(time.isBefore(now.minusMinutes(30)));
                count.incrementAndGet();duration.addAndGet(((Number)event[16]).longValue());
                if(event[4].equals("PAGE_VIEW")) {
                    views.merge(time.toLocalDate(),1,Integer::sum);
                    visitors.add((String)event[2]);sessions.add((String)event[3]);
                }
            }
            return null;
        }).when(repository).insert(anyList());
        var service=new AnalyticsTestDataService(repository,analytics,collector,clock);
        try {
            long expected=now.toLocalDate().minusDays(59).datesUntil(now.toLocalDate().plusDays(1))
                    .mapToLong(AnalyticsTestDataService::dailyPageViews).sum();
            for(int run=0;run<2;run++) {
                resets.set(0);
                views.clear();visitors.clear();sessions.clear();count.set(0);duration.set(0);
                var job=new ConcurrentHashMap<String,Object>();
                service.generate(7,now,job);
                assertEquals("COMPLETED",job.get("status"));
                assertEquals(60,job.get("completedDays"));
                assertEquals(60,views.size());
                assertEquals(now.toLocalDate().minusDays(59),views.firstKey());
                assertEquals(now.toLocalDate(),views.lastKey());
                views.forEach((date,value)->assertEquals(AnalyticsTestDataService.dailyPageViews(date),value));
                assertTrue(expected>=60000 && expected<=120000);
                assertEquals(expected,job.get("pageViews"));
                assertEquals(count.get(),job.get("createdEvents"));
                var totals=(Map<?,?>)job.get("expectedTotals");
                assertEquals(visitors.size(),totals.get("visitors"));
                assertEquals((long)sessions.size(),totals.get("sessions"));
                assertEquals(duration.get(),totals.get("totalDurationSeconds"));
                assertEquals(expected,totals.get("pageViews"));
            }
            verify(analytics,times(2)).beginTestDataReset(7);
            verify(analytics,times(2)).finishTestDataReset(7);
            verify(repository,times(120)).rebuildDay(eq(7L),any());
            verify(repository,never()).testCount(anyLong(),any());
        } finally {service.close();}
    }

    @Test void failedDeletionNeverStartsGeneration() {
        when(analytics.beginTestDataReset(7)).thenThrow(new IllegalStateException("delete failed"));
        var service=new AnalyticsTestDataService(repository,analytics,collector,clock);
        try {
            var job=new ConcurrentHashMap<String,Object>();service.generate(7,now,job);
            assertEquals("FAILED",job.get("status"));verifyNoInteractions(repository);
            verify(analytics,never()).finishTestDataReset(anyLong());
        } finally {service.close();}
    }

    @Test void failedGenerationReleasesResetAndReportsFailure() {
        when(analytics.beginTestDataReset(7)).thenReturn(Map.of());
        doThrow(new IllegalStateException("insert failed")).when(repository).insert(anyList());
        var service=new AnalyticsTestDataService(repository,analytics,collector,clock);
        try {
            var job=new ConcurrentHashMap<String,Object>();service.generate(7,now,job);
            assertEquals("FAILED",job.get("status"));verify(analytics).finishTestDataReset(7);
        } finally {service.close();}
    }

    @Test void deletionIsScopedToOneConferenceAcrossAllFourTables() {
        var jdbc=mock(JdbcTemplate.class);var actual=new AnalyticsRepository(jdbc);
        when(jdbc.update(anyString(),eq(7L))).thenReturn(3);
        var deleted=actual.deleteConferenceAnalytics(7);
        assertEquals(3L,deleted.get("deletedEvents"));
        for(String table:List.of("analytics_events","analytics_daily_facts","analytics_daily_summary","analytics_daily_dimensions"))
            verify(jdbc).update("DELETE FROM "+table+" WHERE conferenceSeq=?",7L);
        verifyNoMoreInteractions(jdbc);
        assertThrows(IllegalArgumentException.class,()->actual.deleteConferenceAnalytics(0));
    }

    @Test void scheduledAggregationWaitsButDashboardRemainsAvailableDuringGeneration() {
        when(repository.conferenceExists(anyLong())).thenReturn(true);
        when(repository.deleteConferenceAnalytics(7)).thenReturn(Map.of());
        var actual=new AnalyticsService(repository);
        try {
            actual.aggregateDirtyDays();
            actual.dirty(7,now.toLocalDate().minusDays(80));
            actual.beginTestDataReset(7);
            actual.dirty(7,now.toLocalDate());actual.dirty(8,now.toLocalDate());
            actual.aggregateDirtyDays();
            verify(repository,never()).rebuildDay(eq(7L),any());
            verify(repository).rebuildDay(8,now.toLocalDate());
            assertDoesNotThrow(()->actual.dashboard(7,null,null));
            actual.finishTestDataReset(7);actual.aggregateDirtyDays();
            verify(repository).rebuildDay(7,now.toLocalDate());
            verify(repository,never()).rebuildDay(7,now.toLocalDate().minusDays(80));
        } finally {actual.close();}
    }

    @Test void confirmationMustMatchHeaderAndOldRequestsCannotDelete() throws Exception {
        var data=mock(AnalyticsTestDataService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AnalyticsController(null,analytics,data,new LicenseProperties())).build();
        mvc.perform(post("/api/admin/testdata/analytics").header("X-Conference-Seq","7")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/testdata/analytics").header("X-Conference-Seq","7").param("confirmedConferenceSeq","8"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(data);
        when(data.start(7)).thenReturn(Map.of("status","RUNNING"));
        mvc.perform(post("/api/admin/testdata/analytics").header("X-Conference-Seq","7").param("confirmedConferenceSeq","7"))
                .andExpect(status().isAccepted());
        verify(data).start(7);
    }
}
