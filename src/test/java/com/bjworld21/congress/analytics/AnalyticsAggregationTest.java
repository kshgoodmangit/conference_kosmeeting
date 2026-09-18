package com.bjworld21.congress.analytics;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsAggregationTest {
    @Test void finalizationRecoversOnStartupRetriesAndLateEventsStillRebuild() {
        var repository=mock(AnalyticsRepository.class);
        var date=LocalDate.of(2026,9,12);
        when(repository.recentDays()).thenReturn(List.of(Map.of("conferenceSeq",1L,"statDate",date))).thenReturn(List.of());
        doThrow(new IllegalStateException("temporary failure")).doNothing().when(repository).refreshDay(1,date);
        var service=new AnalyticsService(repository);
        service.aggregateDirtyDays();
        service.aggregateDirtyDays();
        service.refreshRecentDays();
        service.aggregateDirtyDays();
        verify(repository,times(2)).refreshDay(1,date);
        service.dirty(1,date);
        service.aggregateDirtyDays();
        verify(repository).rebuildDay(1,date);
    }
    @Test void failedBackfillBlocksOnlyAffectedConferenceAndRetries() {
        var repository=mock(AnalyticsRepository.class);
        when(repository.conferenceExists(anyLong())).thenReturn(true);
        var date=LocalDate.of(2026,8,1);
        when(repository.pendingDays()).thenReturn(List.of(Map.of("conferenceSeq",1L,"statDate",date)));
        doThrow(new IllegalStateException("temporary failure")).doNothing().when(repository).rebuildDay(1,date);
        var service=new AnalyticsService(repository);
        assertThrows(AnalyticsService.AggregationPendingException.class,()->service.mapCountries(1));
        service.aggregateDirtyDays();
        assertThrows(AnalyticsService.AggregationPendingException.class,()->service.mapCountries(1));
        assertDoesNotThrow(()->service.mapCountries(2));
        service.aggregateDirtyDays();
        assertDoesNotThrow(()->service.mapCountries(1));
        verify(repository,times(2)).rebuildDay(1,date);
        verify(repository,times(1)).pendingDays();
    }

    @Test void periodicExpiryDoesNotForceRawRebuildButNewEventDoes() {
        var repository=mock(AnalyticsRepository.class);
        var date=LocalDate.of(2026,9,13);
        when(repository.recentDays()).thenReturn(List.of(Map.of("conferenceSeq",1L,"statDate",date)));
        var service=new AnalyticsService(repository);
        service.refreshRecentDays();
        service.aggregateDirtyDays();
        verify(repository).refreshDay(1,date);
        verify(repository,never()).rebuildDay(anyLong(),any());
        service.dirty(1,date);
        service.aggregateDirtyDays();
        verify(repository).rebuildDay(1,date);
    }

    @Test void eventsArrivingDuringAggregationRemainQueued() {
        var repository=mock(AnalyticsRepository.class);
        var service=new AnalyticsService(repository);
        var date=LocalDate.of(2026,9,13);
        doAnswer(call->{service.dirty(1,date);return null;}).doNothing().when(repository).rebuildDay(1,date);
        service.dirty(1,date);
        service.aggregateDirtyDays();
        service.aggregateDirtyDays();
        service.aggregateDirtyDays();
        verify(repository,times(2)).rebuildDay(1,date);
    }
}
