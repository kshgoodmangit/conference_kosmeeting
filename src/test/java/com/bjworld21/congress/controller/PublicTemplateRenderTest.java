package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.MenuSettingsResponse;
import com.bjworld21.congress.dto.ProgramManagementResponse;
import com.bjworld21.congress.dto.SpeakerPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;

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
                new com.bjworld21.congress.dto.PublicPopupDisplay.Item(1L, "Welcome to APDRC8",
                        "<img src='/public/img/main/main-top3.png' alt='Conference'><p>Connecting minds, advancing discovery. Join us in Seoul for APDRC8.</p>", null, "/registration/online-registration"),
                new com.bjworld21.congress.dto.PublicPopupDisplay.Item(2L, "Abstract submission",
                        "<img src='/public/img/main/main-top1.png' alt='Research'><p>Share your latest research with the international Drosophila community.</p>", null, null),
                new com.bjworld21.congress.dto.PublicPopupDisplay.Item(3L, "Scientific program",
                        "<img src='/public/img/main/main-top2.png' alt='Program'><p>Discover invited talks, scientific sessions and opportunities to connect.</p>", null, null),
                new com.bjworld21.congress.dto.PublicPopupDisplay.Item(4L, "Registration opens soon", "<p>We look forward to welcoming you.</p>", null, null));
        var directory = java.nio.file.Path.of("build", "popup-preview");
        java.nio.file.Files.createDirectories(directory);
        for (int layout = 1; layout <= 8; layout++) {
            context.setVariable("popupDisplay", new com.bjworld21.congress.dto.PublicPopupDisplay(layout, items));
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

        MenuSettingsResponse currentMenu = menu("welcome-message", "Welcome Message", "/program/welcome-message", List.of());
        currentMenu.setParentKey("program");
        MenuSettingsResponse sectionMenu = menu("program", "PROGRAM", "/program", List.of(currentMenu));

        context = new Context();
        context.setVariable("pageTitle", "Scientific Program | APDRC8");
        context.setVariable("pageDescription", "Conference program");
        context.setVariable("canonicalUrl", "https://conference.example/program/scientific-program");
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
