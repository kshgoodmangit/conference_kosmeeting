package com.bjworld21.conference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Bjworld21CmsConferenceApplicationTests {

    // Exercise the real MVC/security configuration without connecting to the configured external DB.
    @MockitoBean
    private javax.sql.DataSource dataSource;

    @MockitoBean
    private com.bjworld21.conference.config.PopupSchemaInitializer popupSchemaInitializer;

    @MockitoBean
    private com.bjworld21.conference.analytics.AnalyticsService analyticsService;

    @MockitoBean
    private com.bjworld21.conference.service.AdminAccessRequestMailService accessRequestMailService;

    @MockitoBean
    private com.bjworld21.conference.config.DailyDashboardTestDataScheduler dashboardScheduler;

    @MockitoBean
    private com.bjworld21.conference.service.ConferenceSettingsService conferences;

    @MockitoBean
    private com.bjworld21.conference.service.MenuSettingsService menus;

    @MockitoBean
    private com.bjworld21.conference.service.AdminIpAccessCache adminIpAccessCache;

    @MockitoBean
    private com.bjworld21.conference.service.AdminCredentialVerifier adminCredentialVerifier;

    @BeforeEach
    void permitTestNetworkWithoutDatabase() {
        when(adminIpAccessCache.isAllowed(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(adminCredentialVerifier.verifyActiveAdministrator("", ""))
                .thenThrow(new IllegalArgumentException("Missing credentials"));
    }

    @Autowired
    private com.bjworld21.conference.publicsite.PublicSiteProperties publicSiteProperties;

    @AfterEach
    void restoreSiteMode() {
        publicSiteProperties.setConferenceMode(com.bjworld21.conference.publicsite.PublicSiteProperties.ConferenceMode.MULTI);
        publicSiteProperties.setDefaultConferenceSeq(1L);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void contextLoads() {
    }

    @ParameterizedTest
    @CsvSource({
            "SINGLE,ko,/welcome-message,apdrc8",
            "SINGLE,both,/ko/welcome-message,apdrc8",
            "MULTI,ko,/apdrc8/welcome-message,apdrc8",
            "MULTI,both,/apdrc8/ko/welcome-message,apdrc8",
            "MULTI,ko,/2026_136/welcome-message,2026_136",
            "MULTI,both,/2026_136/ko/welcome-message,2026_136"
    })
    void rendersLocalizedCmsThroughRealRoutingAndSecurity(String mode, String languages, String path, String sitePath) throws Exception {
        publicSiteProperties.setConferenceMode(com.bjworld21.conference.publicsite.PublicSiteProperties.ConferenceMode.valueOf(mode));
        publicSiteProperties.setDefaultConferenceSeq(1L);
        var conference = com.bjworld21.conference.dto.ConferenceSettingsResponse.builder()
                .seq(1L).sitePath(sitePath).eventName("Scoped conference").defaultLanguage("ko")
                .supportedLanguages("both".equals(languages) ? List.of("ko", "en") : List.of("ko")).build();
        when(conferences.getSettings(1L)).thenReturn(conference);
        when(conferences.getSettingsBySitePath(sitePath)).thenReturn(conference);
        var menu = com.bjworld21.conference.entity.MenuSettings.builder()
                .seq(10L).menuKey("welcome-message").menuName("환영사").menuScope("user").menuType("page")
                .routePath("/welcome-message").menuPath("welcome-message")
                .menuHtml("<p>학회별 국문 본문</p><a href='/login'>로그인</a>")
                .translationReady(true).enabled(true).navigationVisible(true).build();
        var menuRepository = org.mockito.Mockito.mock(com.bjworld21.conference.repository.MenuSettingsRepository.class);
        when(menuRepository.findActiveByScope(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(menu));
        var translations = org.mockito.Mockito.mock(com.bjworld21.conference.service.MenuTranslationService.class);
        when(translations.localize(1L, List.of(menu), "ko")).thenReturn(List.of(menu));
        var menuService = new com.bjworld21.conference.service.MenuSettingsService(menuRepository,
                org.mockito.Mockito.mock(com.bjworld21.conference.service.MenuHtmlHistoryService.class));
        menuService.setTranslationService(translations);
        when(menus.getActiveUserMenuTree(1L, "ko")).thenAnswer(ignored -> menuService.getActiveUserMenuTree(1L, "ko"));
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String prefix = path.substring(0, path.lastIndexOf('/'));
        assertThat(body).contains("학회별 국문 본문", "data-conference-seq=\"1\"", "data-language=\"ko\"",
                "data-api-base=\"/api/public/1\"", "href=\"" + prefix + "/login\"");
        assertThat(org.jsoup.Jsoup.parse(body).select(".header-menu a").eachAttr("href"))
                .contains(prefix + "/welcome-message")
                .doesNotContain(prefix + "/old-folder/welcome-message");
        org.mockito.Mockito.verifyNoInteractions(adminIpAccessCache);
    }

    @Test
    void underscoreConferenceHomeRedirectsToDefaultLanguageThroughRealRouting() throws Exception {
        publicSiteProperties.setConferenceMode(com.bjworld21.conference.publicsite.PublicSiteProperties.ConferenceMode.MULTI);
        when(conferences.getSettingsBySitePath("2026_136")).thenReturn(
                com.bjworld21.conference.dto.ConferenceSettingsResponse.builder().seq(1L).sitePath("2026_136")
                        .defaultLanguage("en").supportedLanguages(List.of("ko", "en")).build());
        mockMvc.perform(get("/2026_136/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/2026_136/en/"));
        org.mockito.Mockito.verifyNoInteractions(adminIpAccessCache);
    }

    @Test
    void servesCkEditorStaticResource() throws Exception {
        mockMvc.perform(get("/vendor/ckeditor4/ckeditor.js"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnsafeApiRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/cache/reload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-CSRF-ERROR", "true"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void acceptsTokenIssuedByCsrfEndpoint() throws Exception {
        MvcResult tokenResult = mockMvc.perform(get("/api/security/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(tokenResult.getResponse().getContentAsByteArray());
        String headerName = tokenResponse.path("headerName").asText();
        String token = tokenResponse.path("token").asText();
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/admin/cache/reload")
                        .session(session)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("X-CSRF-ERROR"));
    }

    @Test
    void protectsCkEditorImageUploadsWithCsrfToken() throws Exception {
        String[] uploadPaths = {
                "/api/admin/boards/images",
                "/api/admin/popups/images",
                "/api/admin/mail/images"
        };
        for (String uploadPath : uploadPaths) {
            mockMvc.perform(multipart(uploadPath)
                            .file(new MockMultipartFile("upload", "image.png", "image/png", new byte[]{1})))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("X-CSRF-ERROR", "true"));
        }

        MvcResult tokenResult = mockMvc.perform(get("/api/security/csrf-token"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenResponse = objectMapper.readTree(tokenResult.getResponse().getContentAsByteArray());
        String headerName = tokenResponse.path("headerName").asText();
        String token = tokenResponse.path("token").asText();
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);

        for (String uploadPath : uploadPaths) {
            mockMvc.perform(multipart(uploadPath)
                            .file(new MockMultipartFile("upload", "image.png", "image/png", new byte[]{1}))
                            .session(session)
                            .header(headerName, token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("X-CSRF-ERROR"));
        }
    }

    @Test
    void doesNotApplyBrowserCsrfProtectionToSignedMailWebhook() throws Exception {
        mockMvc.perform(post("/api/webhooks/mail/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(header().doesNotExist("X-CSRF-ERROR"));
    }

    @Test
    void separatesPublicAndAdminApiPaths() {
        Set<String> paths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(paths).contains(
                "/api/public/{conferenceSeq}/conference-settings",
                "/api/countries/used",
                "/api/admin/members/register",
                "/api/public/{conferenceSeq}/registration-fees",
                "/api/public/{conferenceSeq}/popups/{seq}/image",
                "/api/public/{conferenceSeq}/sponsors/{seq}/logo",
                "/api/public/{conferenceSeq}/members/register",
                "/api/admin/conference-settings",
                "/api/admin/conference-settings/{seq}",
                "/api/admin/countries",
                "/api/admin/countries/{seq}/is-used",
                "/api/admin/abstracts",
                "/api/admin/abstract-evaluation-items",
                "/api/admin/common-codes",
                "/api/admin/menu-settings",
                "/api/admin/members",
                "/api/admin/popups",
                "/api/admin/popups/{seq}",
                "/api/admin/registration-fees",
                "/api/admin/sponsors",
                "/api/admin/sponsors/types",
                "/api/admin/sponsors/{seq}",
                "/api/admin/me/password",
                "/api/admin/sponsorship-applications",
                "/api/admin/sponsorship-applications/{seq}",
                "/api/admin/sponsorship-applications/{seq}/business-license"
        );
        assertThat(paths).doesNotContain(
                "/api/conference-settings",
                "/api/members/register",
                "/api/registration-fees",
                "/api/sponsorship-applications",
                "/api/public/members/login",
                "/api/public/abstracts",
                "/api/conference-settings/list",
                "/api/abstracts",
                "/api/abstract-evaluation-items",
                "/api/common-codes",
                "/api/countries",
                "/api/menu-settings",
                "/api/members",
                "/api/members/{seq}",
                "/api/popups/page",
                "/api/popups/{seq}",
                "/api/sponsors/page",
                "/api/sponsors/types",
                "/api/sponsors/{seq}",
                "/api/admin-account/me/password",
                "/api/sponsorship-applications/{seq}"
        );
    }

}
