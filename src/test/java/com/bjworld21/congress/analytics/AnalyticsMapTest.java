package com.bjworld21.congress.analytics;

import com.bjworld21.congress.config.LicenseProperties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsMapTest {
    @Test void mapQueryUsesOnlyRequestedConferenceWithoutDateOrCountryLimit() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = new AnalyticsRepository(jdbc);
        repository.allTimeCountries(7L);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(7L));
        assertTrue(sql.getValue().contains("conferenceSeq=?"));
        assertTrue(sql.getValue().contains("analytics_daily_facts"));
        assertTrue(sql.getValue().contains("pageViewCount>0"));
        assertFalse(sql.getValue().contains("analytics_events"));
        assertTrue(sql.getValue().contains("COUNT(DISTINCT visitorIdHash)"));
        assertFalse(sql.getValue().contains("occurredAt"));
        assertFalse(sql.getValue().toUpperCase().contains("LIMIT"));
    }

    @Test void mapReadsRemainUncachedDuringLoadingTestsAndValidateConference() {
        var repository = mock(AnalyticsRepository.class);
        when(repository.conferenceExists(1)).thenReturn(true);
        when(repository.conferenceExists(2)).thenReturn(true);
        List<Map<String,Object>> first = List.of(Map.of("dimensionKey", "FR", "visitors", 15));
        List<Map<String,Object>> second = List.of(Map.of("dimensionKey", "US", "visitors", 20));
        when(repository.allTimeCountries(1)).thenReturn(first);
        when(repository.allTimeCountries(2)).thenReturn(second);
        var service = new AnalyticsService(repository);
        service.aggregateDirtyDays();
        assertEquals(first, service.mapCountries(1));
        assertEquals(second, service.mapCountries(2));
        assertEquals(first, service.mapCountries(1));
        verify(repository, times(2)).allTimeCountries(1);
        verify(repository, times(1)).allTimeCountries(2);
        service.invalidate();
        assertEquals(first, service.mapCountries(1));
        verify(repository, times(3)).allTimeCountries(1);
        assertThrows(IllegalArgumentException.class, () -> service.mapCountries(99));
        verify(repository, never()).allTimeCountries(99);
    }

    @Test void mapCountriesAreIndependentOfPeriodCountriesAndOnlyLoadedWhenRequested() {
        var service = mock(AnalyticsService.class);
        var controller = new AnalyticsController(null, service, null, new LicenseProperties());
        var end = LocalDate.of(2026,9,13);
        var start = end.minusDays(29);
        // FR was visited outside the current report period and must still appear on the map.
        List<Map<String,Object>> recent = List.of(Map.of("dimensionKey", "KR"));
        List<Map<String,Object>> allTime = List.of(Map.of("dimensionKey", "KR"), Map.of("dimensionKey", "FR"));
        when(service.overview(7,start,end)).thenReturn(Map.of("countries", recent));
        when(service.mapCountries(7)).thenReturn(allTime);
        var details = (Map<?,?>) controller.overview(7,start,end,false).getBody();
        assertNotNull(details);
        assertEquals(recent, details.get("countries"));
        assertFalse(details.containsKey("mapCountries"));
        verify(service, never()).mapCountries(anyLong());
        var board = (Map<?,?>) controller.overview(7,start,end,true).getBody();
        assertNotNull(board);
        assertEquals(recent, board.get("countries"));
        assertEquals(allTime, board.get("mapCountries"));
        verify(service).mapCountries(7);
    }
}
