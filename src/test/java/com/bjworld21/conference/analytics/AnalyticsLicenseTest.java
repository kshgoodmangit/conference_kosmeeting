package com.bjworld21.conference.analytics;

import com.bjworld21.conference.config.LicenseProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsLicenseTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dashboardRequiresItsOwnLicenseRegardlessOfEventDashboard(boolean eventEnabled) throws Exception {
        var license = new LicenseProperties();
        license.setEventDashboardEnabled(eventEnabled);
        var service = mock(AnalyticsService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(null, service, null, license)).build();
        mvc.perform(get("/api/admin/analytics/dashboard").header("X-Conference-Seq", "1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        when(service.overview(eq(1L), any(), any())).thenReturn(Map.of());
        mvc.perform(get("/api/admin/analytics").header("X-Conference-Seq", "1"))
                .andExpect(status().isOk());

        license.setUserAnalyticsDashboardEnabled(true);
        when(service.dashboard(1, null, null)).thenReturn(Map.of());
        mvc.perform(get("/api/admin/analytics/dashboard").header("X-Conference-Seq", "1"))
                .andExpect(status().isOk());
        verify(service).dashboard(1, null, null);
    }
}
