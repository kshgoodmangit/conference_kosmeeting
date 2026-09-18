package com.bjworld21.congress.analytics;

import com.bjworld21.congress.config.LicenseProperties;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalyticsDashboardTest {
    private static LicenseProperties enabledLicense() {
        var license = new LicenseProperties();
        license.setUserAnalyticsDashboardEnabled(true);
        return license;
    }

    final AnalyticsRepository repository=mock(AnalyticsRepository.class);
    final LocalDate end=LocalDate.of(2026,9,12);
    final LocalDate start=end.minusDays(29);

    @Test void dashboardUsesConferenceTotalsAndOnlyVisibleDimensions() {
        var current=Map.<String,Object>of("visitors",17,"sessions",23,"pageViews",99,"bouncedSessions",3);
        var previous=Map.<String,Object>of("visitors",11,"sessions",19,"pageViews",66,"bouncedSessions",4);
        when(repository.allTimeTotals(7,true)).thenReturn(current);
        when(repository.previousConference(7)).thenReturn(Optional.of(new AnalyticsRepository.PreviousConference(3,"Previous event")));
        when(repository.allTimeTotals(3,false)).thenReturn(previous);
        when(repository.allTimeTrend(7)).thenReturn(List.of(
                Map.of("date",end.minusDays(120).toString(),"visitors",5,"pageViews",30),
                Map.of("date",end.toString(),"visitors",12,"pageViews",69)));
        var result=new AnalyticsSnapshotReader(repository).overview(7,start,end,true);
        assertEquals(current,result.get("totals"));assertEquals(previous,result.get("previous"));
        assertEquals(new AnalyticsRepository.PreviousConference(3,"Previous event"),result.get("previousConference"));
        verify(repository,never()).totals(anyLong(),any(),any());
        verify(repository,never()).allTimeTotals(3,true);
        assertEquals("Asia/Seoul",result.get("timezone"));
        var trend=(List<?>)result.get("trend");assertEquals(121,trend.size());
        assertEquals(Map.of("date",end.minusDays(119).toString(),"visitors",0,"pageViews",0),trend.get(1));
        assertEquals(end.minusDays(120).toString(),result.get("startDate"));
        assertEquals(end.toString(),result.get("endDate"));
        verify(repository).allTimeSources(7);
        verify(repository,never()).dimension(anyLong(),any(),any(),anyString());
        verify(repository,never()).trend(anyLong(),any(),any());
        for(String key:List.of("devices","browsers","operatingSystems","hourly","pages"))assertFalse(result.containsKey(key));
        verify(repository,never()).hourly(anyLong(),any(),any());verify(repository,never()).exits(anyLong(),any(),any());
        verify(repository,never()).dimension(anyLong(),any(),any(),eq("pagePath"));
        verify(repository,never()).dimension(anyLong(),any(),any(),eq("deviceType"));
        verify(repository,never()).dimension(anyLong(),any(),any(),eq("browserFamily"));
        verify(repository,never()).dimension(anyLong(),any(),any(),eq("osFamily"));
    }

    @Test void detailStillIncludesAllDimensionsHourlyAndExitCounts() {
        var previous=Map.<String,Object>of("visitors",11);
        when(repository.totals(1,start.minusDays(30),start.minusDays(1))).thenReturn(previous);
        var page=new HashMap<String,Object>(Map.of("dimensionKey","/program","pageViews",3));
        when(repository.dimension(1,start,end,"pagePath")).thenReturn(List.of(page));
        when(repository.exits(1,start,end)).thenReturn(List.of(Map.of("pagePath","/program","exits",2)));
        var result=new AnalyticsSnapshotReader(repository).overview(1,start,end,false);
        for(String key:List.of("devices","browsers","operatingSystems","hourly","pages"))assertTrue(result.containsKey(key));
        assertEquals(2L,page.get("exits"));verify(repository).hourly(1,start,end);
        assertEquals(previous,result.get("previous"));
        verify(repository,never()).previousConference(anyLong());
        verify(repository,never()).allTimeTotals(anyLong(),anyBoolean());
        for(String key:List.of("deviceType","browserFamily","osFamily"))verify(repository).dimension(1,start,end,key);
    }

    @Test void dashboardReadsRemainUncachedDuringLoadingTestsAndRealtimeRemainsLive() {
        when(repository.conferenceExists(anyLong())).thenReturn(true);
        when(repository.allTimeCountries(1)).thenReturn(List.of(Map.of("dimensionKey","FR")));
        var service=new AnalyticsService(repository);
        try {
            service.aggregateDirtyDays();
            var result=service.dashboard(1,start,end);
            assertEquals(List.of(Map.of("dimensionKey","FR")),result.get("mapCountries"));
            assertSame(result.get("mapCountries"),result.get("countries"));
            service.dirty(1,end);service.aggregateDirtyDays();
            service.dashboard(1,start,end);
            verify(repository,times(2)).allTimeTotals(1,true);
            verify(repository,times(2)).allTimeCountries(1);
            verify(repository,times(2)).realtime(1);
            assertFalse(service.overview(1,start,end).containsKey("mapCountries"));
            verify(repository).totals(1,start,end);
            service.dashboard(2,start,end);service.dashboard(1,end,end);
            verify(repository).allTimeTotals(2,true);verify(repository,times(3)).allTimeTotals(1,true);
        } finally {service.close();}
    }

    @Test void dashboardEndpointDispatchesLightweightReadAndKeepsErrors() {
        var service=mock(AnalyticsService.class);var controller=new AnalyticsController(null,service,null,enabledLicense());
        when(service.dashboard(1,null,null)).thenReturn(Map.of("mapCountries",List.of()));
        var response=controller.dashboard(1);
        assertEquals(200,response.getStatusCode().value());assertEquals("no-store",response.getHeaders().getCacheControl());
        verify(service,never()).overview(anyLong(),any(),any());
        when(service.dashboard(2,null,null)).thenThrow(new IllegalArgumentException("invalid"));
        assertEquals(400,controller.dashboard(2).getStatusCode().value());
    }

    @Test void mvcUsesConferenceOnlyEvenWithLegacyDatesAndReturnsBackfillRetry() throws Exception {
        var service=mock(AnalyticsService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AnalyticsController(null,service,null,enabledLicense())).build();
        var response=new HashMap<String,Object>();
        response.put("mapCountries",List.of());response.put("countries",List.of());
        response.put("previousConference",null);response.put("previous",null);
        when(service.dashboard(1,null,null)).thenReturn(response);
        mvc.perform(get("/api/admin/analytics/dashboard").header("X-Conference-Seq","1")
                .param("startDate",start.toString()).param("endDate",end.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.mapCountries").isArray()).andExpect(jsonPath("$.devices").doesNotExist())
                .andExpect(jsonPath("$.previous").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.previousConference").value(org.hamcrest.Matchers.nullValue()));
        verify(service).dashboard(1,null,null);
        mvc.perform(get("/api/admin/analytics/dashboard")).andExpect(status().isBadRequest());
        when(service.dashboard(2,null,null)).thenThrow(new AnalyticsService.AggregationPendingException());
        mvc.perform(get("/api/admin/analytics/dashboard").header("X-Conference-Seq","2")
                .param("startDate",start.toString()).param("endDate",end.toString()))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","10"));
    }
}
