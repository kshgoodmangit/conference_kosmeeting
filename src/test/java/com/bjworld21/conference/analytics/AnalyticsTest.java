package com.bjworld21.conference.analytics;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.service.ConferenceSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsTest {
    @Test void externalCountryDatabaseResolvesPublicIpAndExcludesLocalIp() throws Exception {
        String path=System.getenv("ANALYTICS_TEST_COUNTRY_DATABASE");
        Assumptions.assumeTrue(path!=null && !path.isBlank(),"Optional external MMDB integration test");
        var resolver=new AnalyticsCountryResolver(path);
        try {
            assertEquals("US",resolver.country("8.8.8.8"));
            assertNull(resolver.country("127.0.0.1"));
            assertNull(resolver.country("192.168.1.1"));
        } finally {resolver.close();}
    }
    @Test void filtersPrivatePathsAndDropsQueryStrings() {
        assertEquals("/information/notice",AnalyticsCollector.cleanPath("/information/notice?email=private#secret"));
        for (String path:List.of("/admin/dashboard","/api/admin","//outside.test/page","https://outside.test/page","/%61dmin"))
            assertThrows(IllegalArgumentException.class,()->AnalyticsCollector.cleanPath(path));
    }
    @Test void classifiesAcquisitionWithoutTrustingLookalikeDomains() {
        assertEquals("search",AnalyticsCollector.source("www.google.com",null,null));
        assertEquals("referral",AnalyticsCollector.source("google.com.evil.test",null,null));
        assertEquals("social",AnalyticsCollector.source(null,"social",null));
        assertEquals("direct",AnalyticsCollector.source(null,null,null));
        assertEquals("search",AnalyticsCollector.source(null,"cpc","newsletter"));
    }
    @Test void tenantHashesAreStableButIsolated() {
        var secret=new PersonalDataProperties();secret.setDbEncString("unit-test-only-secret");
        var collector=new AnalyticsCollector(null,null,null,null,null,secret);
        assertEquals(64,collector.hash("visitor:1","uuid").length());
        assertEquals(collector.hash("visitor:1","uuid"),collector.hash("visitor:1","uuid"));
        assertNotEquals(collector.hash("visitor:1","uuid"),collector.hash("visitor:2","uuid"));
        assertNotEquals(collector.hash("visitor:1","uuid"),collector.hash("session:1","uuid"));
    }
    @Test void rejectsInvalidPeriodsBeforeQuerying() {
        var repository=mock(AnalyticsRepository.class);
        when(repository.conferenceExists(1)).thenReturn(true);
        var service=new AnalyticsService(repository);
        var today=LocalDate.now(AnalyticsService.ZONE);
        assertThrows(IllegalArgumentException.class,()->service.overview(1,today.minusDays(90),today));
        assertThrows(IllegalArgumentException.class,()->service.overview(1,today,today.plusDays(1)));
        assertThrows(IllegalArgumentException.class,()->service.overview(2,today,today));
        verify(repository,never()).totals(anyLong(),any(),any());
    }
    @Test void testVolumeIsReproducibleAndWithinRequestedBounds() {
        LocalDate start=LocalDate.of(2026,7,16);
        for(var date=start;date.isBefore(start.plusDays(60));date=date.plusDays(1)) {
            int count=AnalyticsTestDataService.dailyPageViews(date);
            assertTrue(count>=1000 && count<=2000);
            assertEquals(count,AnalyticsTestDataService.dailyPageViews(date));
        }
        assertEquals(AnalyticsTestDataService.id("same"),AnalyticsTestDataService.id("same"));
        assertNotEquals(AnalyticsTestDataService.id("first"),AnalyticsTestDataService.id("second"));
    }
    @Test void collectionUsesServerConferenceAndNeverPersistsRawIdentifiers() {
        var repo=mock(AnalyticsRepository.class);var service=mock(AnalyticsService.class);
        var geo=mock(AnalyticsCountryResolver.class);var ip=mock(ClientIpResolver.class);
        var conferences=mock(ConferenceSettingsService.class);
        when(conferences.getLatestConferenceSeq()).thenReturn(1L);
        var secret=new PersonalDataProperties();secret.setDbEncString("unit-test-secret");
        var request=new MockHttpServletRequest();request.addHeader("User-Agent","Mozilla/5.0 (iPhone) AppleWebKit Safari/604.1");
        request.addHeader("X-Conference-Seq","999"); request.setAttribute(com.bjworld21.conference.publicsite.PublicSiteContext.ATTRIBUTE, com.bjworld21.conference.publicsite.PublicSiteTestContext.context(1, "en"));
        when(ip.resolve(request)).thenReturn("127.0.0.1");
        var collector=new AnalyticsCollector(repo,service,geo,ip,conferences,secret);
        String id=UUID.randomUUID().toString();
        assertTrue(collector.collect(new AnalyticsCollector.Event(id,id,id,"PAGE_VIEW","/?s=private","Home",null,null,null,null,50,Instant.now()),request));
        verify(repo).insert(argThat(rows->{
            Object[] row=rows.get(0);
            return row[0].equals(1L) && row[5].equals("/") && !row[2].equals(id)
                    && row[12].equals("mobile") && row[14].equals("iOS") && row[16].equals(0);
        }));
    }
}
