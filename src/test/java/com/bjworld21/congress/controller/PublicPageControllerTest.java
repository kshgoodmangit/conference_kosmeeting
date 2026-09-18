package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.dto.MenuSettingsResponse;
import com.bjworld21.congress.dto.MemberDetailResponse;
import com.bjworld21.congress.dto.MemberListResponse;
import com.bjworld21.congress.dto.ProgramManagementResponse;
import com.bjworld21.congress.service.*;
import com.bjworld21.congress.publicsite.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class PublicPageControllerTest {
    private MockMvc mockMvc;
    private MemberService memberService;
    private ConferenceSettingsService conferenceSettingsService;
    private MenuSettingsService menuSettingsService;
    private BoardPostService boardPostService;
    private com.bjworld21.congress.repository.PopupRepository popupRepository;
    private PopupLayoutSettingsService popupLayouts;

    @BeforeEach
    void setUp() {
        menuSettingsService = mock(MenuSettingsService.class);
        conferenceSettingsService = mock(ConferenceSettingsService.class);
        ProgramService programService = mock(ProgramService.class);
        SpeakerService speakerService = mock(SpeakerService.class);
        boardPostService = mock(BoardPostService.class);
        SponsorService sponsorService = mock(SponsorService.class);
        memberService = mock(MemberService.class);
        popupRepository = mock(com.bjworld21.congress.repository.PopupRepository.class);
        popupLayouts = mock(PopupLayoutSettingsService.class);

        MenuSettingsResponse currentMenu = MenuSettingsResponse.builder()
                .seq(2L)
                .menuScope("user")
                .menuKey("scientific-program")
                .parentKey("program")
                .menuName("Scientific Program")
                .menuType("page")
                .pathType("full")
                .routePath("/scientific-program")
                .navigationVisible(true)
                .menuHtml("<h2>Program</h2><script>unsafe()</script>")
                .sortOrder(10)
                .enabled(true)
                .children(List.of())
                .build();
        MenuSettingsResponse sectionMenu = MenuSettingsResponse.builder()
                .seq(1L)
                .menuScope("user")
                .menuKey("program")
                .menuName("PROGRAM")
                .menuType("folder")
                .pathType("segment")
                .routePath("/program")
                .navigationVisible(true)
                .sortOrder(10)
                .enabled(true)
                .children(List.of(currentMenu, MenuSettingsResponse.builder()
                        .seq(3L).menuScope("user").menuKey("program-at-a-glance").parentKey("program")
                        .menuName("Program at a Glance").menuType("page").pathType("full")
                        .routePath("/program-at-a-glance").enabled(true).navigationVisible(true)
                        .menuHtml("<ul class='program-overview-stats'><li>08 Session Tracks</li></ul>"
                                + "<p>Introduction</p><script>unsafe()</script>")
                        .children(List.of()).build()))
                .build();
        MenuSettingsResponse abstractMenu = MenuSettingsResponse.builder()
                .seq(12L).menuScope("user").menuKey("mypage-abstract").parentKey("mypage")
                .menuName("Abstract Submission").menuType("page").pathType("full")
                .routePath("/mypage-abstract").navigationVisible(true).authRequired(true)
                .menuHtml("").sortOrder(10).enabled(true).children(List.of()).build();
        MenuSettingsResponse myPageMenu = MenuSettingsResponse.builder()
                .seq(11L).menuScope("user").menuKey("mypage").parentKey("member")
                .menuName("My Page").menuType("page").pathType("full")
                .routePath("/mypage").navigationVisible(true).authRequired(true)
                .menuHtml("").sortOrder(10).enabled(true).children(List.of(abstractMenu)).build();
        MenuSettingsResponse loginMenu = MenuSettingsResponse.builder()
                .seq(13L).menuScope("user").menuKey("login").parentKey("member")
                .menuName("Login").menuType("page").pathType("full")
                .routePath("/login").navigationVisible(false).authRequired(false)
                .menuHtml("").sortOrder(5).enabled(true).children(List.of()).build();
        MenuSettingsResponse memberMenu = MenuSettingsResponse.builder()
                .seq(10L).menuScope("user").menuKey("member").menuName("MEMBER")
                .menuType("folder").pathType("segment").routePath("/member")
                .navigationVisible(true).menuHtml("").sortOrder(20).enabled(true)
                .children(List.of(loginMenu, myPageMenu)).build();
        MenuSettingsResponse abstractSubmissionMenu = MenuSettingsResponse.builder()
                .seq(20L).menuScope("user").menuKey("abstract-submission").menuName("Abstract Submission")
                .menuType("page").pathType("full").routePath("/abstract-submission")
                .navigationVisible(true).menuHtml("").sortOrder(30).enabled(true).children(List.of()).build();
        MenuSettingsResponse onlineRegistrationMenu = MenuSettingsResponse.builder()
                .seq(21L).menuScope("user").menuKey("online-registration").menuName("Online Registration")
                .menuType("page").pathType("full").routePath("/online-registration")
                .navigationVisible(true).menuHtml("").sortOrder(40).enabled(true).children(List.of()).build();
        when(conferenceSettingsService.getLatestConferenceSeq()).thenReturn(1L);
        when(menuSettingsService.getActiveUserMenuTree(1L, "en")).thenReturn(List.of(
                sectionMenu, memberMenu, abstractSubmissionMenu, onlineRegistrationMenu
        ));
        when(conferenceSettingsService.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("apdrc8").defaultLanguage("en").supportedLanguages(List.of("en"))
                .seq(1L)
                .eventName("APDRC8")
                .eventStartDate(LocalDate.of(2026, 10, 1))
                .eventEndDate(LocalDate.of(2026, 10, 3))
                .regularStartDate(LocalDate.now().plusDays(50))
                .abstractEndDate(LocalDate.now().plusDays(30))
                .venueAddress("Seoul, Korea")
                .build());
        when(programService.getManagementData(1L)).thenReturn(ProgramManagementResponse.builder()
                .days(List.of())
                .items(List.of())
                .build());

        PublicSiteProperties siteProperties = new PublicSiteProperties();
        siteProperties.setConferenceMode(PublicSiteProperties.ConferenceMode.SINGLE);
        siteProperties.setDefaultConferenceSeq(1L);
        PublicPageController controller = new PublicPageController(
                menuSettingsService,
                conferenceSettingsService,
                new CmsHtmlSanitizer(),
                programService,
                speakerService,
                boardPostService,
                sponsorService,
                memberService,
                Clock.system(ZoneId.of("Asia/Seoul")),
                new PublicPopupService(popupRepository, popupLayouts,
                        new PublicPopupPreferences(Clock.system(ZoneId.of("Asia/Seoul"))),
                        new CmsHtmlSanitizer(), Clock.system(ZoneId.of("Asia/Seoul"))), new PublicSiteService(conferenceSettingsService, siteProperties)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void programOverviewReplacesCmsStatisticsWithLiveScheduleCounts() throws Exception {
        var result = mockMvc.perform(get("/program-at-a-glance"))
                .andExpect(status().isOk()).andReturn();
        String html = (String) result.getModelAndView().getModel().get("contentHtml");
        assertThat(html).contains("Conference Days", "Scientific Sessions", "Plenary &amp; Presidential Lectures",
                        "<strong>00</strong>", "Introduction")
                .doesNotContain("Session Tracks", "unsafe()", "<script>");
    }

    @Test
    void oldMenuPathsAreNotSilentlyMatchedToNewPageNames() throws Exception {
        var menu = MenuSettingsResponse.builder().seq(90L).menuKey("welcome-message")
                .menuName("Welcome").routePath("/apdrc8/welcome-message").menuType("page")
                .children(List.of()).build();
        when(menuSettingsService.getActiveUserMenuTree(1L, "en")).thenReturn(List.of(menu));
        mockMvc.perform(get("/welcome-message"))
                .andExpect(status().isNotFound()).andExpect(view().name("public/not-found"));
    }

    @Test
    void protectedNoticeDetailsAndHomePreviewRequireThisConferencesLoginBeforeReadingPosts() throws Exception {
        configureNotice(true);
        mockMvc.perform(get("/notice-detail?seq=12").session(memberSession(2L)))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:/login"));
        mockMvc.perform(get("/"))
                .andExpect(status().isOk()).andExpect(model().attribute("notices", List.of()));
        org.mockito.Mockito.verifyNoInteractions(boardPostService);
    }

    @Test
    void noticeCanonicalUrlIncludesOnlyItsIdentity() throws Exception {
        configureNotice(false);
        when(boardPostService.getPublishedNotice(1L, 12L)).thenReturn(
                com.bjworld21.congress.dto.BoardPostResponse.builder().seq(12L).title("Notice")
                        .content("<p>Published</p>").attachments(List.of()).build());
        mockMvc.perform(get("/notice-detail?seq=12&page=3&tracking=ignored"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canonicalUrl", "http://localhost/notice-detail?seq=12"));
    }

    private void configureNotice(boolean protectedParent) {
        var notice = MenuSettingsResponse.builder().seq(31L).menuKey("notice").menuName("Notice")
                .parentKey("information").routePath("/notice").menuType("board").boardSeq(1L)
                .authRequired(false).children(List.of()).build();
        var parent = MenuSettingsResponse.builder().seq(30L).menuKey("information").menuName("Information")
                .routePath("/information").menuType("folder").authRequired(protectedParent)
                .children(List.of(notice)).build();
        when(menuSettingsService.getActiveUserMenuTree(1L, "en")).thenReturn(List.of(parent));
    }

    @Test
    void passwordChangePageRequiresLoginAndRendersWithoutANewMenuRow() throws Exception {
        mockMvc.perform(get("/mypage-password"))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:/login"));
        when(memberService.findDetail(1L, 11L)).thenReturn(MemberDetailResponse.builder()
                .member(MemberListResponse.builder().seq(11L).email("member@example.com")
                        .firstName("Jane").lastName("Doe").build()).abstractSubmissions(List.of()).build());
        var result = mockMvc.perform(get("/mypage-password").session(memberSession(1L)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("contentTemplate", "public/member/mypage-password"))
                .andExpect(model().attribute("secureAccountPage", true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andReturn();
        var resolver = new org.thymeleaf.templateresolver.FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var messages = new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("public-ui"); messages.setDefaultEncoding("UTF-8");
        engine.setTemplateEngineMessageSource(messages);
        var context = new org.thymeleaf.context.Context(Locale.ENGLISH, result.getModelAndView().getModel());
        context.setVariable("_csrf", new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf"));
        String html = engine.process("public/page", context);
        assertThat(html).contains("/public/js/password-change.js", "data-change-csrf", "test-csrf",
                        "autocomplete=\"current-password\"", "autocomplete=\"new-password\"", "Change Password")
                .doesNotContain("/public/js/analytics.js", "ajax.googleapis.com");
    }

    @Test
    void recoveryPagesRenderWithoutCmsRowsAndDoNotLoadAnalyticsOrThirdPartyScripts() throws Exception {
        for (String path : List.of("/forgot-password", "/reset-password")) {
            var result = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(view().name("public/page"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(model().attribute("secureAccountPage", true))
                    .andReturn();
            var resolver = new org.thymeleaf.templateresolver.FileTemplateResolver();
            resolver.setPrefix("src/main/resources/templates/");
            resolver.setSuffix(".html");
            resolver.setCharacterEncoding("UTF-8");
            var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
        var messages = new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("public-ui"); messages.setDefaultEncoding("UTF-8");
        engine.setTemplateEngineMessageSource(messages);
            var context = new org.thymeleaf.context.Context(Locale.ENGLISH, result.getModelAndView().getModel());
            context.setVariable("_csrf", new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf"));
            String html = engine.process("public/page", context);
            assertThat(html).contains("/public/js/password-reset.js", "data-reset-csrf", "test-csrf")
                    .doesNotContain("/public/js/analytics.js", "ajax.googleapis.com");
            assertThat(html).contains(path.equals("/reset-password") ? "id=\"member-reset-form\"" : "id=\"member-recovery-form\"");
        }
    }

    @Test
    void rendersHomeWithActiveMenus() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/home"))
                .andExpect(model().attribute("eventName", "APDRC8"))
                .andExpect(model().attribute("registrationDday", "D-50"))
                .andExpect(model().attribute("abstractSubmissionDday", "D-30"))
                .andExpect(model().attribute("registrationOpened", false))
                .andExpect(model().attribute("abstractOpened", true))
                .andExpect(model().attributeExists("navigationMenus"));
    }

    @Test
    void hiddenPreferenceSkipsPopupQueriesAndPreventsSharedPageCaching() throws Exception {
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        mockMvc.perform(get("/").cookie(new jakarta.servlet.http.Cookie("conference_popups_hidden_1", today.toString())))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("popupDisplay"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "private, no-store"));
        org.mockito.Mockito.verifyNoInteractions(popupRepository, popupLayouts);
    }

    @Test
    void preservesOtherConferenceSessionWithoutTreatingItAsCurrentLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        PublicMemberSession.signIn(session, 2L, 11L, "fingerprint");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("public/home"))
                .andExpect(model().attribute("eventName", "APDRC8"))
                .andExpect(model().attribute("memberLoggedIn", false));

        assertThat(session.isInvalid()).isFalse();
        assertThat(PublicMemberSession.resolve(session, 2L)).isNotNull();
    }

    @Test
    void showsTheCurrentRegistrationPeriodAndRemainingDaysOnMyPage() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        MockHttpSession session = new MockHttpSession();
        PublicMemberSession.signIn(session, 1L, 11L, "fingerprint");
        when(memberService.findDetail(1L, 11L)).thenReturn(MemberDetailResponse.builder()
                .member(MemberListResponse.builder().seq(11L).firstName("Jane").lastName("Doe").build())
                .abstractSubmissions(List.of())
                .build());
        when(conferenceSettingsService.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("apdrc8").defaultLanguage("en").supportedLanguages(List.of("en"))
                .seq(1L)
                .eventName("APDRC8")
                .earlyBirdStartDate(today.minusDays(1))
                .earlyBirdEndDate(today.plusDays(5))
                .regularStartDate(today.plusDays(6))
                .regularEndDate(today.plusDays(15))
                .build());

        mockMvc.perform(get("/mypage").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("mypageRegistrationLabel", "Early-bird"))
                .andExpect(model().attribute("mypageRegistrationDday", "D-5"));

        when(conferenceSettingsService.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("apdrc8").defaultLanguage("en").supportedLanguages(List.of("en"))
                .seq(1L)
                .eventName("APDRC8")
                .earlyBirdStartDate(today.minusDays(20))
                .earlyBirdEndDate(today.minusDays(1))
                .regularStartDate(today)
                .regularEndDate(today.plusDays(10))
                .build());

        mockMvc.perform(get("/mypage").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("mypageRegistrationLabel", "Regular"))
                .andExpect(model().attribute("mypageRegistrationDday", "D-10"));
    }

    @Test
    void marksHomeRegistrationClosedAfterItsEndDate() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(conferenceSettingsService.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("apdrc8").defaultLanguage("en").supportedLanguages(List.of("en"))
                .seq(1L)
                .eventName("APDRC8")
                .regularStartDate(today.minusDays(30))
                .regularEndDate(today.minusDays(1))
                .build());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("registrationDday", "CLOSED"))
                .andExpect(model().attribute("registrationOpened", false));
    }

    @Test
    void opensHomeCtasDuringConfiguredPeriodsAndUsesEarlyBirdStartDate() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(conferenceSettingsService.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("apdrc8").defaultLanguage("en").supportedLanguages(List.of("en"))
                .seq(1L)
                .eventName("APDRC8")
                .earlyBirdStartDate(today)
                .earlyBirdEndDate(today.plusDays(10))
                .regularStartDate(today.plusDays(20))
                .regularEndDate(today.plusDays(30))
                .abstractStartDate(today)
                .abstractEndDate(today.plusDays(10))
                .build());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("registrationOpenDateText",
                        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).format(today)))
                .andExpect(model().attribute("registrationDday", "OPEN"))
                .andExpect(model().attribute("registrationOpened", true))
                .andExpect(model().attribute("abstractOpened", true));
    }

    @Test
    void usesRegularStartDateAfterEarlyBirdRegistrationEnds() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate regularStartDate = today.plusDays(2);
        when(conferenceSettingsService.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("apdrc8").defaultLanguage("en").supportedLanguages(List.of("en"))
                .eventName("APDRC8")
                .earlyBirdStartDate(today.minusDays(30))
                .earlyBirdEndDate(today.minusDays(1))
                .regularStartDate(regularStartDate)
                .regularEndDate(today.plusDays(30))
                .build());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("registrationOpenDateText",
                        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).format(regularStartDate)))
                .andExpect(model().attribute("registrationDday", "D-2"))
                .andExpect(model().attribute("registrationOpened", false));
    }

    @Test
    void rendersCmsPageAndSanitizesHtml() throws Exception {
        mockMvc.perform(get("/scientific-program"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/page"))
                .andExpect(model().attribute("currentTopMenuKey", "program"))
                .andExpect(model().attribute("contentHtml", "<h2>Program</h2>"))
                .andExpect(model().attributeExists("programData"));
    }

    @Test
    void returnsPublishedNotFoundPageForUnknownRoute() throws Exception {
        mockMvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("public/not-found"));
    }

    @Test
    void rendersLoginWithoutRedirectingBackToLogin() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/page"))
                .andExpect(model().attribute("contentTemplate", "public/member/login"));
    }

    @Test
    void rendersMemberAbstractReviewThroughTheAbstractMenu() throws Exception {
        when(memberService.findDetail(1L, 11L)).thenReturn(MemberDetailResponse.builder()
                .member(MemberListResponse.builder().seq(11L).firstName("Jane").lastName("Doe").build())
                .abstractSubmissions(List.of())
                .build());

        mockMvc.perform(get("/abstract-review")
                        .param("seq", "31")
                        .session(memberSession(1L)))
                .andExpect(status().isOk())
                .andExpect(view().name("public/page"))
                .andExpect(model().attribute("contentTemplate", "public/member/mypage-abstract-review"));
    }

    @Test
    void blocksApplicationPagesOutsideTheirConfiguredPeriods() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(conferenceSettingsService.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("apdrc8").defaultLanguage("en").supportedLanguages(List.of("en"))
                .eventName("APDRC8")
                .abstractEndDate(today.minusDays(1))
                .earlyBirdEndDate(today.minusDays(1))
                .regularStartDate(today.plusDays(1))
                .build());

        mockMvc.perform(get("/abstract-submission"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/page"))
                .andExpect(model().attribute("contentTemplate", "public/access-notice"))
                .andExpect(model().attribute("accessNotice", "abstract-closed"));
        mockMvc.perform(get("/online-registration"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/page"))
                .andExpect(model().attribute("contentTemplate", "public/access-notice"))
                .andExpect(model().attribute("accessNotice", "registration-closed"));
    }

    @Test
    void doesNotClaimVendorStaticResourcePath() throws Exception {
        mockMvc.perform(get("/vendor/ckeditor4/ckeditor.js"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getHandler()).isNull());
    }

    @Test
    void publishesRobotsAndSitemap() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sitemap: http://localhost/sitemap.xml")));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/scientific-program")));
    }
    private MockHttpSession memberSession(long conferenceSeq) {
        var session = new MockHttpSession();
        PublicMemberSession.signIn(session, conferenceSeq, 11L, "fingerprint");
        return session;
    }}
