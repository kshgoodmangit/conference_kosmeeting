package com.bjworld21.conference.analytics;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsDefaultPeriodTest {
    private final LocalDate today=LocalDate.of(2026,9,19);
    private final AnalyticsRepository repository=mock(AnalyticsRepository.class);

    @Test void historicalConferencesUseTheirOwnRecordedPeriods() {
        when(repository.conferenceExists(anyLong())).thenReturn(true);
        var older=new AnalyticsRepository.DateRange(LocalDate.of(2025,8,4),LocalDate.of(2025,10,2));
        var newer=new AnalyticsRepository.DateRange(LocalDate.of(2026,1,2),LocalDate.of(2026,3,2));
        when(repository.availablePeriod(6,today)).thenReturn(Optional.of(older));
        when(repository.availablePeriod(5,today)).thenReturn(Optional.of(newer));
        var service=new AnalyticsService(repository);
        try {
            service.aggregateDirtyDays();
            assertEquals(older,service.defaultPeriod(6,today));
            assertEquals(newer,service.defaultPeriod(5,today));
        } finally { service.close(); }
    }

    @Test void recentAndEmptyConferencesKeepThirtyDaysAndLongHistoryIsCapped() {
        when(repository.conferenceExists(anyLong())).thenReturn(true);
        when(repository.availablePeriod(1,today)).thenReturn(Optional.of(
                new AnalyticsRepository.DateRange(today.minusDays(200),today.minusDays(29))));
        when(repository.availablePeriod(2,today)).thenReturn(Optional.empty());
        var last=today.minusDays(30);
        when(repository.availablePeriod(3,today)).thenReturn(Optional.of(
                new AnalyticsRepository.DateRange(today.minusDays(200),last)));
        var service=new AnalyticsService(repository);
        try {
            service.aggregateDirtyDays();
            var recent=new AnalyticsRepository.DateRange(today.minusDays(29),today);
            assertEquals(recent,service.defaultPeriod(1,today));
            assertEquals(recent,service.defaultPeriod(2,today));
            assertEquals(new AnalyticsRepository.DateRange(last.minusDays(89),last),service.defaultPeriod(3,today));
            assertThrows(IllegalArgumentException.class,()->service.defaultPeriod(0,today));
        } finally { service.close(); }
    }

    @Test void explicitDatesAreNeverReplacedByHistoricalDefaults() {
        var service=mock(AnalyticsService.class);
        var controller=new AnalyticsController(null,service,null,null);
        var period=new AnalyticsRepository.DateRange(LocalDate.of(2025,8,4),LocalDate.of(2025,10,2));
        when(service.defaultPeriod(6)).thenReturn(period);
        when(service.overview(anyLong(),any(),any())).thenReturn(Map.of());
        assertEquals(200,controller.overview(6,null,null,false).getStatusCode().value());
        verify(service).overview(6,period.startDate(),period.endDate());
        clearInvocations(service);
        assertEquals(200,controller.overview(6,today.minusDays(29),today,false).getStatusCode().value());
        verify(service).overview(6,today.minusDays(29),today);
        verify(service,never()).defaultPeriod(anyLong());
    }
}
