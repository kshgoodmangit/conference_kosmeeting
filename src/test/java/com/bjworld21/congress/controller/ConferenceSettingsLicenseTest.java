package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.service.ConferenceSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConferenceSettingsLicenseTest {
    private final LicenseProperties license = new LicenseProperties();
    private final ConferenceSettingsService service = mock(ConferenceSettingsService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConferenceSettingsController(service, license)).build();

    @Test
    void eventDashboardIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/admin/conference-settings/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conferenceCopyEnabled").value(false))
                .andExpect(jsonPath("$.eventDashboardEnabled").value(false))
                .andExpect(jsonPath("$.userAnalyticsDashboardEnabled").value(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsConfiguredCapability(boolean enabled) throws Exception {
        license.setConferenceCreationEnabled(enabled);
        license.setAbstractSimilarityEnabled(enabled);
        license.setEventDashboardEnabled(enabled);
        license.setUserAnalyticsDashboardEnabled(!enabled);
        mvc.perform(get("/api/admin/conference-settings/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conferenceCreationEnabled").value(enabled))
                .andExpect(jsonPath("$.conferenceCopyEnabled").value(enabled))
                .andExpect(jsonPath("$.abstractSimilarityEnabled").value(enabled))
                .andExpect(jsonPath("$.eventDashboardEnabled").value(enabled))
                .andExpect(jsonPath("$.userAnalyticsDashboardEnabled").value(!enabled));
    }

    @Test
    void creationIsForbiddenByDefaultWithoutCallingService() throws Exception {
        mvc.perform(post("/api/admin/conference-settings").param("eventName", "Example"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void enabledLicenseAllowsCreation() throws Exception {
        license.setConferenceCreationEnabled(true);
        mvc.perform(post("/api/admin/conference-settings").param("eventName", "Example"))
                .andExpect(status().isOk());
        verify(service).saveSettings("Example", null, null, null, null, null, null, "USD", null, null, null, null, null);
    }

    @Test
    void disabledLicenseStillAllowsExistingConferenceUpdates() throws Exception {
        mvc.perform(put("/api/admin/conference-settings/1").param("eventName", "Example"))
                .andExpect(status().isOk());
        verify(service).updateSettings(1L, "Example", null, null, null, null, null, null, "USD", null, null, null, null, null);
    }
}
