package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.AdminSessionInterceptor;
import com.bjworld21.congress.config.LicenseProperties;
import com.bjworld21.congress.service.ShowcaseDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShowcaseDashboardControllerTest {
    private final ShowcaseDashboardService service = mock(ShowcaseDashboardService.class);
    private final LicenseProperties license = new LicenseProperties();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ShowcaseDashboardController(service, license))
            .addInterceptors(new AdminSessionInterceptor()).build();

    @Test void requiresFullAdministratorSession() throws Exception {
        license.setEventDashboardEnabled(true);
        mvc.perform(get("/api/admin/dashboard/board").header("X-Conference-Seq", 5)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard/board").header("X-Conference-Seq", 5)
                .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "reviewer")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void usesSelectedHeaderAndRejectsMissingOrInvalidScope() throws Exception {
        license.setEventDashboardEnabled(true);
        mvc.perform(get("/api/admin/dashboard/board").header("X-Conference-Seq", 6)
                .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")).andExpect(status().isOk());
        verify(service).getDashboard(6L);
        mvc.perform(get("/api/admin/dashboard/board").sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/dashboard/board").header("X-Conference-Seq", 0)
                .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }

    @Test void licenseAndUnknownConferenceAreEnforced() throws Exception {
        mvc.perform(get("/api/admin/dashboard/board").header("X-Conference-Seq", 5)
                .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        license.setEventDashboardEnabled(true);
        when(service.getDashboard(999L)).thenThrow(new IllegalArgumentException("Unknown conference"));
        mvc.perform(get("/api/admin/dashboard/board").header("X-Conference-Seq", 999)
                .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")).andExpect(status().isNotFound());
    }
}
