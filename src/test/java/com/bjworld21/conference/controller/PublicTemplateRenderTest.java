package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.MenuSettingsResponse;
import com.bjworld21.conference.dto.ProgramManagementResponse;
import com.bjworld21.conference.dto.SpeakerPageResponse;
import com.bjworld21.conference.publicsite.PublicSiteContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicTemplateRenderTest {
    private SpringTemplateEngine templateEngine;
    private Context context;

    @Test
    void omitsPopupContentButKeepsManualOpenAvailableWhenServerSuppressesPopups() {
        String html = templateEngine.process("public/home", context);
        assertThat(html).doesNotContain("id=\"conference-popups\"")
                .contains("data-popup-open", "/public/js/popups.js", "/public/css/popups.css");
        assertThat(templateEngine.process("public/page", context))
                .doesNotContain("id=\"conference-popups\"")
                .containsOnlyOnce("data-popup-open")
                .containsOnlyOnce("/public/js/popups.js")
                .containsOnlyOnce("/public/css/popups.css");
    }

    @Test
    void rendersAllPopupLayoutsAndKeepsAutomaticPopupContentOffSubpages() throws Exception {
        var items = List.of(
                new com.bjworld21.conference.dto.PublicPopupDisplay.Item(1L, "Welcome to APDRC8",
                        "<img src='/public/img/main/main-top3.png' alt='Conference'><p>Connecting minds, advancing discovery. Join us in Seoul for APDRC8.</p>", null, "/online-registration"),
                new com.bjworld21.conference.dto.PublicPopupDisplay.Item(2L, "Abstract submission",
                        "<img src='/public/img/main/main-top1.png' alt='Research'><p>Share your latest research with the international Drosophila community.</p>", null, null),
                new com.bjworld21.conference.dto.PublicPopupDisplay.Item(3L, "Scientific program",
                        "<img src='/public/img/main/main-top2.png' alt='Program'><p>Discover invited talks, scientific sessions and opportunities to connect.</p>", null, null),
                new com.bjworld21.conference.dto.PublicPopupDisplay.Item(4L, "Registration opens soon", "<p>We look forward to welcoming you.</p>", null, null));
        var directory = java.nio.file.Path.of("build", "popup-preview");
        java.nio.file.Files.createDirectories(directory);
        for (int layout = 1; layout <= 8; layout++) {
            context.setVariable("popupDisplay", new com.bjworld21.conference.dto.PublicPopupDisplay(layout, items));
            String html = templateEngine.process("public/home", context);
            assertThat(html).containsOnlyOnce("id=\"conference-popups\"")
                    .contains("data-layout=\"" + layout + "\"", "Connecting minds, advancing discovery", "Do not show again today")
                    .containsOnlyOnce("/public/js/popups.js");
            assertThat(templateEngine.process("public/page", context)).doesNotContain("id=\"conference-popups\"");
            String fragment = templateEngine.process("public/popups", java.util.Set.of("panel"), context);
            assertThat(fragment).containsOnlyOnce("id=\"conference-popups\"")
                    .contains("Connecting minds, advancing discovery")
                    .doesNotContain("<script", "<link");
            java.nio.file.Files.writeString(directory.resolve("layout-" + layout + ".html"), html, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode("HTML");

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("public-ui");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        templateEngine.setTemplateEngineMessageSource(messages);

        MenuSettingsResponse currentMenu = menu("welcome-message", "Welcome Message", "/welcome-message", List.of());
        currentMenu.setParentKey("program");
        MenuSettingsResponse sectionMenu = menu("program", "PROGRAM", "/program", List.of(currentMenu));

        context = new Context(Locale.ENGLISH);
        setSiteContext("/apdrc8/en", "en", List.of("en", "ko"));
        context.setVariable("pageTitle", "Scientific Program | APDRC8");
        context.setVariable("pageDescription", "Conference program");
        context.setVariable("canonicalUrl", "https://conference.example/scientific-program");
        context.setVariable("eventName", "APDRC8");
        context.setVariable("eventDateText", "2026.10.01 – 2026.10.03");
        context.setVariable("venueText", "Seoul, Korea");
        context.setVariable("navigationMenus", List.of(sectionMenu));
        context.setVariable("loginMenu", null);
        context.setVariable("joinMenu", null);
        context.setVariable("myPageMenu", null);
        context.setVariable("memberLoggedIn", false);
        context.setVariable("currentMenu", currentMenu);
        context.setVariable("currentTopMenuKey", "program");
        context.setVariable("currentYear", 2026);
        context.setVariable("programData", ProgramManagementResponse.builder()
                .days(List.of())
                .items(List.of())
                .build());
        context.setVariable("speakersData", new SpeakerPageResponse(List.of(), 1, 4, 0, 1, 0, 0));
        context.setVariable("notices", List.of());
        context.setVariable("sponsors", List.of());
        context.setVariable("registrationOpenDateText", "September 1, 2027");
        context.setVariable("registrationDday", "D-50");
        context.setVariable("abstractSubmissionDateText", "January 15, 2027");
        context.setVariable("abstractSubmissionDday", "D-30");
        context.setVariable("programDateFormatter", java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH));
        context.setVariable("contentHtml", "<p>Published content</p>");
        context.setVariable("sectionMenu", sectionMenu);
        context.setVariable("breadcrumbs", List.of(sectionMenu, currentMenu));
        context.setVariable("requestedPath", "/missing");
    }

    @Test
    void rendersAllPublicTemplates() {
        assertThat(templateEngine.process("public/home", context)).contains("Connecting Minds", "Invited Speakers");
        assertThat(templateEngine.process("public/page", context)).contains("Published content");
        context.setVariable("contentTemplate", "public/pages/invited-speakers");
        assertThat(templateEngine.process("public/page", context)).contains("speaker-section");
        assertThat(templateEngine.process("public/not-found", context)).contains("404");
    }

    @Test
    void breadcrumbFoldersAreLabelsWhilePageAncestorsRemainLinks() {
        var parent = menu("sponsors", "SPONSORS", "/sponsors", List.of());
        parent.setMenuType("folder");
        var current = menu("sponsorship", "Sponsorship", "/sponsorship", List.of());
        context.setVariable("currentMenu", current);
        context.setVariable("breadcrumbs", List.of(parent, current));

        String html = templateEngine.process("public/page", context);
        String breadcrumb = html.substring(html.indexOf("<ul class=\"breadcrumb\">"));
        breadcrumb = breadcrumb.substring(0, breadcrumb.indexOf("</ul>"));
        assertThat(breadcrumb).contains("<span>SPONSORS</span>", "<span>Sponsorship</span>")
                .doesNotContain("href=\"/apdrc8/en/sponsors\"");

        parent.setMenuType("page");
        html = templateEngine.process("public/page", context);
        breadcrumb = html.substring(html.indexOf("<ul class=\"breadcrumb\">"));
        breadcrumb = breadcrumb.substring(0, breadcrumb.indexOf("</ul>"));
        assertThat(breadcrumb).contains("href=\"/apdrc8/en/sponsors\"");
    }

    @Test
    void includesCookieChoicesOnceAcrossPublicLayoutsBeforeAnalytics() {
        for (String template : List.of("public/home", "public/page", "public/not-found")) {
            String html = templateEngine.process(template, context);
            assertThat(html).containsOnlyOnce("id=\"cookie-banner\"")
                    .containsOnlyOnce("id=\"cookie-preferences\"")
                    .contains("Essential Only", "Accept All", "Save Preferences", "data-cookie-settings")
                    .containsSubsequence("/public/js/cookie-consent.js", "/public/js/analytics.js");
        }
    }

    @Test
    void rendersOnlyLoggedInActionsForMembers() {
        context.setVariable("memberLoggedIn", true);
        context.setVariable("loginMenu", menu("login", "Login", "/login", List.of()));
        context.setVariable("joinMenu", menu("join", "Sign Up", "/join", List.of()));
        context.setVariable("myPageMenu", menu("mypage", "My Page", "/mypage", List.of()));

        assertThat(templateEngine.process("public/home", context))
                .contains("LOGOUT", "MY PAGE")
                .doesNotContain("LOGIN", "SIGN UP");
    }

    @Test
    void desktopAndMobileMenusUseVisibleChildDestinationsAndRenderStandalonePages() {
        var hidden = menu("hidden", "Hidden", "/hidden", List.of());
        hidden.setNavigationVisible(false);
        var visible = menu("welcome-message", "Welcome", "/welcome-message", List.of());
        var folder = menu("about", "About", "/about", List.of(hidden, visible));
        folder.setMenuType("folder");
        var standalone = menu("notice", "Notice", "/notice", List.of());
        var external = menu("external", "External", "https://example.org/path", List.of());
        external.setMenuType("link");
        external.setLinkUrl("https://example.org/path");
        context.setVariable("navigationMenus", List.of(folder, standalone, external));
        setSiteContext("/2026_136/en", "en", List.of("ko", "en"));

        var html = org.jsoup.Jsoup.parse(templateEngine.process("public/home", context));
        for (String selector : List.of(".header-menu", ".mobile-menu-list")) {
            var navigation = html.selectFirst(selector);
            assertThat(navigation.select("a").eachAttr("href"))
                    .contains("/2026_136/en/welcome-message", "/2026_136/en/notice", "https://example.org/path")
                    .doesNotContain("/2026_136/en/about", "/2026_136/en/hidden");
            assertThat(navigation.selectFirst("li > a").attr("href")).isEqualTo("/2026_136/en/welcome-message");
        }
    }

    @Test
    void rendersScopedLinksAndApiActionsInEveryConferenceAndLanguageMode() {
        for (String base : List.of("", "/ko", "/apdrc8", "/apdrc8/ko")) {
            setSiteContext(base, "ko", base.endsWith("/ko") ? List.of("en", "ko") : List.of("ko"));
            context.setLocale(Locale.KOREAN);
            context.setVariable("loginMenu", menu("login", "로그인", "/login", List.of()));
            context.setVariable("joinMenu", menu("join", "회원가입", "/join", List.of()));
            String home = templateEngine.process("public/home", context);
            assertThat(home).contains("lang=\"ko\"",
                            "href=\"" + base + "/welcome-message\"", "href=\"" + base + "/login\"",
                            "href=\"" + base + "/online-registration\"", "로그인", "초청 연자")
                    .doesNotContain("href=\"/program/", "href=\"/registration/", "??public.ui.");
            if (!base.isEmpty()) assertThat(home).contains("data-site-base=\"" + base + "\"");
            context.setVariable("contentTemplate", "public/member/login");
            assertThat(templateEngine.process("public/page", context))
                    .contains("action=\"/api/public/8/members/login?lang=ko\"", "data-success-url=\"" + base + "/\"")
                    .doesNotContain("action=\"/api/public/members/", "??public.ui.");
            context.removeVariable("contentTemplate");
        }
    }

    @Test
    void rendersLanguageChoicesOnlyForMultilingualSitesAndLocalizedMissingContent() {
        setSiteContext("/apdrc8/ko", "ko", List.of("ko", "en"));
        context.setLocale(Locale.KOREAN);
        context.setVariable("languageLinks", Map.of("ko", "/apdrc8/ko/notice-detail?seq=12", "en", "/apdrc8/en/notice-detail?seq=12"));
        context.setVariable("contentHtml", "");
        assertThat(templateEngine.process("public/page", context))
                .contains("콘텐츠를 준비 중입니다.", "href=\"/apdrc8/en/notice-detail?seq=12\"", "hreflang=\"en\"", "한국어")
                .doesNotContain("Published content", "??public.ui.");
        context.setVariable("languageLinks", Map.of("ko", "/welcome-message"));
        assertThat(templateEngine.process("public/page", context)).doesNotContain("public-language-switch", "hreflang=");
    }

    @Test
    void rendersFlatMemberLinksAndAbstractIdentifiersInEveryUrlMode() {
        context.setVariable("mypageAbstracts", List.of(Map.of(
                "seq", 123L, "status", "draft", "submissionNo", "AB-123", "title", "Draft abstract")));
        for (String base : List.of("", "/ko", "/apdrc8", "/apdrc8/ko")) {
            setSiteContext(base, "ko", base.endsWith("/ko") ? List.of("en", "ko") : List.of("ko"));
            assertThat(templateEngine.process("public/member/join", context))
                    .contains("href=\"" + base + "/join-domestic\"", "href=\"" + base + "/join-international\"")
                    .doesNotContain(base + "/join/domestic", base + "/join/international");
            assertThat(templateEngine.process("public/member/mypage-abstract", context))
                    .contains("href=\"" + base + "/mypage-abstract\"", "href=\"" + base + "/mypage-registration\"",
                            "href=\"" + base + "/abstract-write\"", "href=\"" + base + "/abstract-write?seq=123\"",
                            "href=\"" + base + "/abstract-review?seq=123\"")
                    .doesNotContain(base + "/mypage/abstract", base + "/mypage/registration");
        }
    }

    @Test
    void accountFormsAndProtectedDownloadsRetainConferenceAndLanguage() {
        setSiteContext("/apdrc8/en", "en", List.of("en", "ko"));
        context.setVariable("mypageMember", Map.of("email", "member@example.com"));
        for (String fragment : List.of("join-domestic", "join-international", "forgot-password", "reset-password", "mypage-password", "mypage-certificate")) {
            context.setVariable("contentTemplate", "public/member/" + fragment);
            String html = templateEngine.process("public/page", context);
            assertThat(html).contains("/api/public/8/members/", "lang=en")
                    .doesNotContain("action=\"/api/public/members/", "href=\"/mypage", "href=\"/forgot-password", "??public.ui.");
        }
    }

    @Test
    void hidesLanguageChoicesOnlyOnPasswordResetTokenPage() {
        context.setVariable("languageLinks", Map.of("ko", "/apdrc8/ko/reset-password", "en", "/apdrc8/en/reset-password"));
        context.setVariable("contentTemplate", "public/member/reset-password");
        context.setVariable("resetTokenPage", true);
        assertThat(templateEngine.process("public/page", context))
                .contains("/api/public/8/members/password-reset/confirm?lang=en")
                .doesNotContain("public-language-switch", "hreflang=");
        context.setVariable("resetTokenPage", false);
        context.setVariable("contentTemplate", "public/member/forgot-password");
        assertThat(templateEngine.process("public/page", context))
                .contains("public-language-switch", "hreflang=\"ko\"", "hreflang=\"en\"");
    }

    @Test
    void boardPaginationAndDownloadsKeepContextAndEncodeQueryValues() {
        setSiteContext("/apdrc8/ko", "ko", List.of("ko", "en"));
        context.setLocale(Locale.KOREAN);
        var attachment = com.bjworld21.conference.dto.BoardAttachmentResponse.builder()
                .seq(9L).originalFilename("notice.pdf")
                .downloadUrl("/api/boards/2/posts/12/attachments/9").build();
        var notice = com.bjworld21.conference.dto.BoardPostResponse.builder().seq(12L).boardSeq(2L)
                .title("공지 제목").content("<p>본문</p>").publishedAt(java.time.LocalDateTime.of(2026, 9, 18, 10, 0))
                .attachments(List.of(attachment)).attachmentCount(1).viewCount(5L).isPinned(false).build();
        var posts = com.bjworld21.conference.dto.BoardPostPageResponse.builder()
                .items(List.of(notice)).page(2).size(10).totalCount(30).totalPages(3).build();
        context.setVariable("noticePage", posts);
        context.setVariable("notice", notice);
        context.setVariable("faqPage", posts);
        context.setVariable("selectedFaqCategory", "registration&payment");
        context.setVariable("faqCategories", List.of(com.bjworld21.conference.dto.BoardCategoryResponse.builder()
                .categoryCode("registration&payment").categoryName("등록 및 결제").build()));
        var servletContext = new org.springframework.mock.web.MockServletContext();
        var request = new org.springframework.mock.web.MockHttpServletRequest(servletContext);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var exchange = org.thymeleaf.web.servlet.JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, response);
        var variables = new java.util.HashMap<String, Object>();
        context.getVariableNames().forEach(name -> variables.put(name, context.getVariable(name)));
        var web = new org.thymeleaf.context.WebContext(exchange, Locale.KOREAN, variables);
        assertThat(templateEngine.process("public/pages/notice", web))
                .contains("/apdrc8/ko/notice-detail?seq=12", "/apdrc8/ko/notice?page=3");
        assertThat(templateEngine.process("public/pages/faq", web))
                .contains("/apdrc8/ko/faq?page=3&amp;category=registration%26payment");
        assertThat(templateEngine.process("public/pages/notice-detail", web))
                .contains("/api/public/8/boards/2/posts/12/attachments/9?lang=ko", "href=\"/apdrc8/ko/notice\"");
    }

    private void setSiteContext(String base, String language, List<String> supported) {
        PublicSiteContext site = new PublicSiteContext(8L, "apdrc8", language, supported, base, "/api/public/8");
        context.setVariable("siteContext", site);
        context.setVariable("siteBasePath", base);
        context.setVariable("apiBasePath", site.apiBasePath());
        context.setVariable("language", language);
        context.setVariable("supportedLanguages", supported);
        context.setVariable("conferenceSeq", site.conferenceSeq());
        context.setVariable("languageLinks", Map.of());
    }

    private MenuSettingsResponse menu(
            String key,
            String name,
            String path,
            List<MenuSettingsResponse> children
    ) {
        return MenuSettingsResponse.builder()
                .seq((long) key.hashCode())
                .menuScope("user")
                .menuKey(key)
                .menuName(name)
                .menuType("page")
                .pathType("full")
                .routePath(path)
                .targetType("self")
                .navigationVisible(true)
                .enabled(true)
                .sortOrder(10)
                .children(children)
                .build();
    }
}
