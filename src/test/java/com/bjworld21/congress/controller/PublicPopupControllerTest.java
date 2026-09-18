package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.SecurityConfig;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.bjworld21.congress.service.PublicPopupPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({SecurityConfig.class, PublicPopupControllerTest.Config.class})
class PublicPopupControllerTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class Config {
        @Bean PublicPopupController controller() {
            var conferences = mock(ConferenceSettingsService.class);
            when(conferences.getLatestConferenceSeq()).thenReturn(7L);
            return new PublicPopupController(conferences, new PublicPopupPreferences(
                    Clock.fixed(Instant.parse("2026-09-15T14:59:30Z"), ZoneOffset.UTC)));
        }
    }

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(new com.bjworld21.congress.publicsite.PublicSiteTestContext()).build(); }

    @Test
    void guestCanSaveAnHttpOnlyCookieUntilKoreanMidnight() throws Exception {
        mvc.perform(post("/api/public/7/popups/dismiss-today").secure(true).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("conference_popups_hidden_7", "2026-09-15"))
                .andExpect(cookie().maxAge("conference_popups_hidden_7", 30))
                .andExpect(cookie().httpOnly("conference_popups_hidden_7", true))
                .andExpect(cookie().secure("conference_popups_hidden_7", true))
                .andExpect(cookie().path("conference_popups_hidden_7", "/"))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void preferenceMutationRequiresCsrf() throws Exception {
        mvc.perform(post("/api/public/7/popups/dismiss-today"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
    }
}
