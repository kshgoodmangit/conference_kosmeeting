package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.dto.MemberDetailResponse;
import com.bjworld21.congress.dto.MenuSettingsResponse;
import com.bjworld21.congress.dto.BoardPostPageResponse;
import com.bjworld21.congress.dto.BoardPostResponse;
import com.bjworld21.congress.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Renders the public Thymeleaf site from the latest conference and its active menu tree.
 *
 * <p>Most routes use the CMS HTML stored on the menu. Menus that need live data are
 * switched to a dedicated template by {@code menuKey} in {@link #page}; keep that key,
 * the template name, and the administrator menu configuration in sync.</p>
 */
@Controller
public class PublicPageController {
    private static final String DEFAULT_EVENT_NAME = "APDRC8";
    private static final DateTimeFormatter MAIN_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter PROGRAM_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private static final Set<String> NO_BOTTOM_PADDING_MENU_KEYS = Set.of(
            "committee",
            "program-at-a-glance",
            "scientific-program",
            "invited-speakers",
            "sponsor"
    );

    private final MenuSettingsService menuSettingsService;
    private final ConferenceSettingsService conferenceSettingsService;
    private final CmsHtmlSanitizer cmsHtmlSanitizer;
    private final ProgramService programService;
    private final SpeakerService speakerService;
    private final BoardPostService boardPostService;
    private final SponsorService sponsorService;
    private final MemberService memberService;
    private final Clock clock;
    private final PublicPopupService publicPopupService;

    public PublicPageController(
            MenuSettingsService menuSettingsService,
            ConferenceSettingsService conferenceSettingsService,
            CmsHtmlSanitizer cmsHtmlSanitizer,
            ProgramService programService,
            SpeakerService speakerService,
            BoardPostService boardPostService,
            SponsorService sponsorService,
            MemberService memberService,
            Clock clock,
            PublicPopupService publicPopupService
    ) {
        this.menuSettingsService = menuSettingsService;
        this.conferenceSettingsService = conferenceSettingsService;
        this.cmsHtmlSanitizer = cmsHtmlSanitizer;
        this.programService = programService;
        this.speakerService = speakerService;
        this.boardPostService = boardPostService;
        this.sponsorService = sponsorService;
        this.memberService = memberService;
        this.clock = clock;
        this.publicPopupService = publicPopupService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.addHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
        Long conferenceSeq = conferenceSettingsService.getLatestConferenceSeq();
        List<MenuSettingsResponse> menus = menuSettingsService.getActiveUserMenuTree(conferenceSeq);
        ConferenceSettingsResponse conference = conferenceSettingsService.getSettings(conferenceSeq);

        addCommonModel(model, request, menus, conference, null);
        model.addAttribute("pageTitle", eventName(conference));
        model.addAttribute("pageDescription", eventName(conference) + " official conference website");
        model.addAttribute("programData", programService.getManagementData(conferenceSeq));
        model.addAttribute("programDateFormatter", PROGRAM_DATE_FORMATTER);
        model.addAttribute("speakersData", speakerService.list(conferenceSeq, 1, 100, "", null, true));
        model.addAttribute("notices", boardPostService.findPublishedNotices(conferenceSeq, 5, false));
        model.addAttribute("sponsors", sponsorService.findVisible(conferenceSeq));
        model.addAttribute("popupDisplay", publicPopupService.forHome(conferenceSeq, request.getCookies()));

        LocalDate today = LocalDate.now(clock);
        LocalDate registrationOpenDate = earlier(
                future(today, conference.getEarlyBirdStartDate()),
                future(today, conference.getRegularStartDate()));
        if (within(today, conference.getEarlyBirdStartDate(), conference.getEarlyBirdEndDate())) {
            registrationOpenDate = conference.getEarlyBirdStartDate();
        }
        if (within(today, conference.getRegularStartDate(), conference.getRegularEndDate())) {
            registrationOpenDate = conference.getRegularStartDate();
        }
        if (registrationOpenDate == null) {
            registrationOpenDate = conference.getRegularStartDate() != null
                    ? conference.getRegularStartDate() : conference.getEarlyBirdStartDate();
        }
        String registrationDday = formatRegistrationDday(conference);
        LocalDate abstractSubmissionDate = conference.getAbstractEndDate();

        model.addAttribute("registrationOpenDateText", registrationOpenDate == null
                ? "To be announced" : MAIN_DATE_FORMATTER.format(registrationOpenDate));
        model.addAttribute("registrationDday", registrationDday);
        model.addAttribute("registrationOpened", "OPEN".equals(registrationDday));
        model.addAttribute("abstractSubmissionDateText", abstractSubmissionDate == null
                ? "To be announced" : MAIN_DATE_FORMATTER.format(abstractSubmissionDate));
        model.addAttribute("abstractSubmissionDday", formatDday(abstractSubmissionDate, "CLOSED"));
        model.addAttribute("abstractOpened",
                within(today, conference.getAbstractStartDate(), conference.getAbstractEndDate()));
        return "public/home";
    }

    @GetMapping(value = {"/forgot-password", "/forgot-password/", "/reset-password", "/reset-password/"}, produces = MediaType.TEXT_HTML_VALUE)
    public String forgotPassword(HttpServletRequest request, HttpServletResponse response, Model model) {
        boolean reset = "/reset-password".equals(normalizeRoutePath(request));
        String title = reset ? "Reset Password" : "Forgot Password";
        String key = reset ? "reset-password" : "forgot-password";
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        Long conferenceSeq = conferenceSettingsService.getLatestConferenceSeq();
        List<MenuSettingsResponse> menus = menuSettingsService.getActiveUserMenuTree(conferenceSeq);
        ConferenceSettingsResponse conference = conferenceSettingsService.getSettings(conferenceSeq);
        // Account recovery is a public utility page, without a separate CMS menu row.
        MenuSettingsResponse recoveryMenu = MenuSettingsResponse.builder()
                .menuKey(key)
                .menuName(title)
                .routePath("/" + key)
                .children(List.of())
                .build();
        addCommonModel(model, request, menus, conference, recoveryMenu);
        model.addAttribute("sectionMenu", recoveryMenu);
        model.addAttribute("breadcrumbs", List.of(recoveryMenu));
        model.addAttribute("memberPage", true);
        model.addAttribute("memberPageTitle", title);
        model.addAttribute("memberPageDescription", "Recover access to your " + eventName(conference) + " account.");
        model.addAttribute("contentTemplate", "public/member/" + key);
        model.addAttribute("pageTitle", title + " | " + eventName(conference));
        model.addAttribute("secureAccountPage", true);
        model.addAttribute("pageDescription", "Recover access to your " + eventName(conference) + " account.");
        model.addAttribute("noBottomPadding", false);
        return "public/page";
    }

    @GetMapping(value = "/information/noticedetail/{seq}", produces = MediaType.TEXT_HTML_VALUE)
    public String noticeDetail(
            @PathVariable Long seq,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        Long conferenceSeq = conferenceSettingsService.getLatestConferenceSeq();
        List<MenuSettingsResponse> menus = menuSettingsService.getActiveUserMenuTree(conferenceSeq);
        ConferenceSettingsResponse conference = conferenceSettingsService.getSettings(conferenceSeq);
        List<MenuSettingsResponse> flattenedMenus = flatten(menus);
        MenuSettingsResponse currentMenu = flattenedMenus.stream()
                .filter(menu -> "notice".equals(menu.getMenuKey()))
                .findFirst()
                .orElse(null);
        BoardPostResponse notice = boardPostService.getPublishedNotice(conferenceSeq, seq);

        if (currentMenu == null || notice == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            addCommonModel(model, request, menus, conference, null);
            model.addAttribute("pageTitle", "Page not found | " + eventName(conference));
            model.addAttribute("pageDescription", "The requested notice could not be found.");
            return "public/not-found";
        }

        notice.setContent(cmsHtmlSanitizer.sanitize(notice.getContent()));
        List<MenuSettingsResponse> lineage = findLineage(flattenedMenus, currentMenu);
        MenuSettingsResponse topMenu = lineage.isEmpty() ? currentMenu : lineage.get(0);
        addCommonModel(model, request, menus, conference, currentMenu);
        model.addAttribute("currentTopMenuKey", topMenu.getMenuKey());
        model.addAttribute("sectionMenu", topMenu);
        model.addAttribute("breadcrumbs", lineage);
        model.addAttribute("contentTemplate", "public/pages/notice-detail");
        model.addAttribute("notice", notice);
        model.addAttribute("pageTitle", currentMenu.getMenuName() + " | " + eventName(conference));
        model.addAttribute("pageDescription", currentMenu.getMenuName() + " - " + eventName(conference));
        model.addAttribute("noBottomPadding", NO_BOTTOM_PADDING_MENU_KEYS.contains(currentMenu.getMenuKey()));
        return "public/page";
    }

    @GetMapping(
            value = {
                    "/{section:^(?!api$|admin$|assets$|public$|vendor$|commoncode$|error$)[^.]+}",
                    "/{section:^(?!api$|admin$|assets$|public$|vendor$|commoncode$|error$)[^.]+}/{*path}"
            },
            produces = MediaType.TEXT_HTML_VALUE
    )
    public String page(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "") String category,
            Model model
    ) {
        Long conferenceSeq = conferenceSettingsService.getLatestConferenceSeq();
        String routePath = normalizeRoutePath(request);
        List<MenuSettingsResponse> menus = menuSettingsService.getActiveUserMenuTree(conferenceSeq);
        ConferenceSettingsResponse conference = conferenceSettingsService.getSettings(conferenceSeq);
        List<MenuSettingsResponse> flattenedMenus = flatten(menus);
        // These child routes intentionally reuse the mypage-abstract menu entry instead of
        // requiring separate administrator-managed menu rows.
        String memberAbstractTemplate = switch (routePath) {
            case "/mypage/abstract/write" -> "public/member/mypage-abstract-write";
            case "/mypage/abstract/review" -> "public/member/mypage-abstract-review";
            default -> null;
        };
        MenuSettingsResponse currentMenu = flattenedMenus.stream()
                .filter(menu -> routePath.equals(normalizeRoutePath(menu.getRoutePath())))
                .findFirst()
                .orElse(null);
        if (currentMenu == null && memberAbstractTemplate != null) {
            currentMenu = flattenedMenus.stream()
                    .filter(menu -> "mypage-abstract".equals(menu.getMenuKey()))
                    .findFirst()
                    .orElse(null);
        }

        boolean passwordChangePage = "/mypage/password".equals(routePath);
        if (passwordChangePage) {
            currentMenu = flattenedMenus.stream().filter(menu -> "mypage".equals(menu.getMenuKey()))
                    .findFirst().orElse(null);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
        }

        if (currentMenu == null || isExternal(currentMenu)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            addCommonModel(model, request, menus, conference, null);
            model.addAttribute("pageTitle", "Page not found | " + eventName(conference));
            model.addAttribute("pageDescription", "The requested conference page could not be found.");
            model.addAttribute("requestedPath", routePath);
            return "public/not-found";
        }

        List<MenuSettingsResponse> lineage = findLineage(flattenedMenus, currentMenu);
        MenuSettingsResponse topMenu = lineage.isEmpty() ? currentMenu : lineage.get(0);
        String contentHtml = cmsHtmlSanitizer.sanitize(currentMenu.getMenuHtml());
        if ("program-at-a-glance".equals(currentMenu.getMenuKey())) {
            contentHtml = ProgramOverviewStats.render(contentHtml, programService.getManagementData(conferenceSeq));
        }
        boolean memberPage = "member".equals(topMenu.getMenuKey()) && !"member".equals(currentMenu.getMenuKey());
        boolean myPage = lineage.stream().anyMatch(menu -> "mypage".equals(menu.getMenuKey()));
        MenuSettingsResponse memberSectionMenu = memberPage
                ? lineage.get(Math.min(1, lineage.size() - 1))
                : currentMenu;
        // A protected parent protects all descendants even when the child row itself is not flagged.
        if ((passwordChangePage || myPage || Boolean.TRUE.equals(currentMenu.getAuthRequired()))
                && !isMemberLoggedIn(request, conferenceSeq)) {
            return "redirect:/login";
        }

        // 접수 페이지는 화면 진입부터 API와 같은 기간 규칙을 적용한다.
        // 고정된 코드만 홈으로 전달해 새로고침 때 경고가 반복되거나 임의 문구가 노출되지 않게 한다.
        String accessNotice = closedPeriodNotice(routePath, conference);

        String pageDescription = cmsHtmlSanitizer.summarize(
                currentMenu.getMenuHtml(),
                currentMenu.getMenuName() + " - " + eventName(conference)
        );

        addCommonModel(model, request, menus, conference, currentMenu);
        model.addAttribute("currentTopMenuKey", topMenu.getMenuKey());
        model.addAttribute("sectionMenu", topMenu);
        model.addAttribute("breadcrumbs", memberPage ? List.of(currentMenu) : lineage);
        model.addAttribute("pageMenuCurrentKey", memberSectionMenu.getMenuKey());
        model.addAttribute("contentHtml", contentHtml);
        model.addAttribute("memberPage", memberPage);

        // page.html renders contentTemplate when present; otherwise it safely renders CMS HTML.
        if (memberPage && currentMenu.getMenuKey().matches("[a-z0-9-]+")) {
            model.addAttribute("memberPageTitle", memberSectionMenu.getMenuName());
            model.addAttribute("memberPageDescription", cmsHtmlSanitizer.summarize(
                    currentMenu.getMenuHtml(),
                    currentMenu.getMenuName()
            ));
            model.addAttribute("contentTemplate", "public/member/" + currentMenu.getMenuKey());
        }
        if (memberAbstractTemplate != null) {
            model.addAttribute("contentTemplate", memberAbstractTemplate);
        }
        if (passwordChangePage) {
            model.addAttribute("contentTemplate", "public/member/mypage-password");
            model.addAttribute("secureAccountPage", true);
            model.addAttribute("passwordChangePage", true);
        }

        if ("scientific-program".equals(currentMenu.getMenuKey())) {
            model.addAttribute("contentTemplate", "public/pages/scientific-program");
            model.addAttribute("programData", programService.getManagementData(conferenceSeq));
            model.addAttribute("programDateFormatter", PROGRAM_DATE_FORMATTER);
        }

        if ("invited-speakers".equals(currentMenu.getMenuKey())) {
            model.addAttribute("contentTemplate", "public/pages/invited-speakers");
            model.addAttribute("speakersTypes", speakerService.types());
            model.addAttribute("speakersData", speakerService.list(conferenceSeq, 1, 100, "", null, true));
        }

        if("abstract-submission".equals(currentMenu.getMenuKey())) {
            model.addAttribute("contentTemplate", "public/pages/abstract-submission");
        }

        if("online-registration".equals(currentMenu.getMenuKey())) {
            model.addAttribute("contentTemplate", "public/pages/online-registration");
        }

        if("notice".equals(currentMenu.getMenuKey())) {
            model.addAttribute("contentTemplate", "public/pages/notice");
            model.addAttribute("noticePage", boardPostService.findPublishedNoticePage(conferenceSeq, page, 10));
        }

        if("notice-detail".equals(currentMenu.getMenuKey())) {
            model.addAttribute("contentTemplate", "public/pages/notice-detail");
        }

        if("faq".equals(currentMenu.getMenuKey())) {
            model.addAttribute("contentTemplate", "public/pages/faq");
            BoardPostPageResponse faqPage = boardPostService.findPublishedFaqPage(conferenceSeq, page, 10, category);
            faqPage.getItems().forEach(faq -> faq.setContent(cmsHtmlSanitizer.sanitize(faq.getContent())));
            model.addAttribute("faqCategories", boardPostService.findFaqCategories());
            model.addAttribute("faqPage", faqPage);
            model.addAttribute("selectedFaqCategory", category == null ? "" : category.trim());
        }

        if("sponsor".equals(currentMenu.getMenuKey())) {
            model.addAttribute("sponsors", sponsorService.findVisible(conferenceSeq));
            model.addAttribute("sponsorTypes", sponsorService.findSponsorTypes());
            model.addAttribute("contentTemplate", "public/pages/sponsor");
        }

        if (accessNotice != null) {
            model.addAttribute("accessNotice", accessNotice);
            model.addAttribute("contentTemplate", "public/access-notice");
        }

        if (myPage) {
            Long memberSeq = ((Number) request.getSession(false).getAttribute("memberSeq")).longValue();
            MemberDetailResponse detail = memberService.findDetail(conferenceSeq, memberSeq);
            model.addAttribute("mypageMember", detail.getMember());
            model.addAttribute("mypageAbstracts", detail.getAbstractSubmissions());
            model.addAttribute("mypageAbstractDday", formatDday(conference.getAbstractEndDate(), "CLOSED"));
            LocalDate today = LocalDate.now(clock);
            String registrationLabel;
            String registrationDday;
            if (within(today, conference.getRegularStartDate(), conference.getRegularEndDate())) {
                registrationLabel = "Regular";
                registrationDday = formatDday(conference.getRegularEndDate(), "CLOSED");
            } else if (within(today, conference.getEarlyBirdStartDate(), conference.getEarlyBirdEndDate())) {
                registrationLabel = "Early-bird";
                registrationDday = formatDday(conference.getEarlyBirdEndDate(), "CLOSED");
            } else {
                LocalDate nextStart = earlier(
                        future(today, conference.getEarlyBirdStartDate()),
                        future(today, conference.getRegularStartDate()));
                registrationLabel = nextStart == null ? "Registration"
                        : nextStart.equals(conference.getEarlyBirdStartDate()) ? "Early-bird opens" : "Regular opens";
                registrationDday = formatRegistrationDday(conference);
            }
            model.addAttribute("mypageRegistrationLabel", registrationLabel);
            model.addAttribute("mypageRegistrationDday", registrationDday);
        }

        model.addAttribute("pageTitle", (passwordChangePage ? "Change Password" : currentMenu.getMenuName()) + " | " + eventName(conference));
        model.addAttribute("pageDescription", pageDescription);
        model.addAttribute("noBottomPadding", NO_BOTTOM_PADDING_MENU_KEYS.contains(currentMenu.getMenuKey()));

        return "public/page";
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots(HttpServletRequest request) {
        return "User-agent: *\n"
                + "Allow: /\n"
                + "Disallow: /admin/\n"
                + "Sitemap: " + siteOrigin(request) + "/sitemap.xml\n";
    }

    @GetMapping("/public/speakers/{seq}/image")
    @ResponseBody
    public ResponseEntity<Resource> speakerImage(@PathVariable Long seq) {
        Resource resource = speakerService.publicImage(conferenceSettingsService.getLatestConferenceSeq(), seq);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(resource);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap(HttpServletRequest request) {
        Long conferenceSeq = conferenceSettingsService.getLatestConferenceSeq();
        String origin = siteOrigin(request);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")
                .append("<url><loc>").append(HtmlUtils.htmlEscape(origin + "/")).append("</loc></url>");

        flatten(menuSettingsService.getActiveUserMenuTree(conferenceSeq)).stream()
                .filter(menu -> !isExternal(menu))
                .map(MenuSettingsResponse::getRoutePath)
                .map(PublicPageController::normalizeRoutePath)
                .filter(path -> path != null && !path.equals("/"))
                .distinct()
                .forEach(path -> xml.append("<url><loc>")
                        .append(HtmlUtils.htmlEscape(origin + path))
                        .append("</loc></url>"));

        return xml.append("</urlset>").toString();
    }

    private void addCommonModel(
            Model model,
            HttpServletRequest request,
            List<MenuSettingsResponse> menus,
            ConferenceSettingsResponse conference,
            MenuSettingsResponse currentMenu
    ) {
        // This is the shared model contract consumed by fragments.html on every public page.
        Map<String, MenuSettingsResponse> menuByKey = new LinkedHashMap<>();
        flatten(menus).forEach(menu -> menuByKey.put(menu.getMenuKey(), menu));

        model.addAttribute("eventName", eventName(conference));
        model.addAttribute("eventDateText", formatDateRange(conference.getEventStartDate(), conference.getEventEndDate()));
        model.addAttribute("venueText", defaultText(conference.getVenueAddress(), "Venue to be announced"));
        model.addAttribute("navigationMenus", menus);
        model.addAttribute("loginMenu", menuByKey.get("login"));
        model.addAttribute("joinMenu", menuByKey.get("join"));
        model.addAttribute("myPageMenu", menuByKey.get("mypage"));
        model.addAttribute("memberLoggedIn", isMemberLoggedIn(request, conference.getSeq()));
        model.addAttribute("currentMenu", currentMenu);
        model.addAttribute("currentTopMenuKey", "");
        model.addAttribute("canonicalUrl", canonicalUrl(request));
        model.addAttribute("currentYear", LocalDate.now(clock).getYear());
    }

    private boolean isMemberLoggedIn(HttpServletRequest request, Long conferenceSeq) {
        var session = request.getSession(false);
        return PublicMemberSession.resolve(session, () -> conferenceSeq) != null;
    }

    private String closedPeriodNotice(String routePath, ConferenceSettingsResponse conference) {
        LocalDate today = LocalDate.now(clock);
        if ("/abstract/abstract-submission".equals(routePath)
                && !within(today, conference.getAbstractStartDate(), conference.getAbstractEndDate())) {
            return "abstract-closed";
        }

        if ("/mypage/abstract/write".equals(routePath)
                && !within(today, conference.getAbstractStartDate(), conference.getAbstractEndDate())) {
            return "abstract-closed";
        }

        if ("/registration/online-registration".equals(routePath)
                && !within(today, conference.getEarlyBirdStartDate(), conference.getEarlyBirdEndDate())
                && !within(today, conference.getRegularStartDate(), conference.getRegularEndDate())) {
            return "registration-closed";
        }

        return null;
    }

    private boolean within(LocalDate today, LocalDate start, LocalDate end) {
        return (start != null || end != null)
                && (start == null || !today.isBefore(start))
                && (end == null || !today.isAfter(end));
    }

    private List<MenuSettingsResponse> findLineage(
            List<MenuSettingsResponse> flattenedMenus,
            MenuSettingsResponse currentMenu
    ) {
        Map<String, MenuSettingsResponse> menuByKey = new LinkedHashMap<>();
        flattenedMenus.forEach(menu -> menuByKey.put(menu.getMenuKey(), menu));

        List<MenuSettingsResponse> lineage = new ArrayList<>();
        MenuSettingsResponse cursor = currentMenu;
        while (cursor != null) {
            lineage.add(0, cursor);
            cursor = cursor.getParentKey() == null ? null : menuByKey.get(cursor.getParentKey());
        }
        return lineage;
    }

    private List<MenuSettingsResponse> flatten(List<MenuSettingsResponse> menus) {
        List<MenuSettingsResponse> flattened = new ArrayList<>();
        for (MenuSettingsResponse menu : menus) {
            flattened.add(menu);
            if (menu.getChildren() != null) {
                flattened.addAll(flatten(menu.getChildren()));
            }
        }
        return flattened;
    }

    private boolean isNavigationVisible(MenuSettingsResponse menu) {
        return !Boolean.FALSE.equals(menu.getNavigationVisible());
    }

    private static boolean isExternal(MenuSettingsResponse menu) {
        return "external".equals(menu.getPathType()) || "link".equals(menu.getMenuType());
    }

    private String eventName(ConferenceSettingsResponse conference) {
        return defaultText(conference.getEventName(), DEFAULT_EVENT_NAME);
    }

    private String formatDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return "Schedule to be announced";
        }
        if (startDate == null) {
            return MAIN_DATE_FORMATTER.format(endDate);
        }
        if (endDate == null || startDate.equals(endDate)) {
            return MAIN_DATE_FORMATTER.format(startDate);
        }
        return MAIN_DATE_FORMATTER.format(startDate) + " – " + MAIN_DATE_FORMATTER.format(endDate);
    }

    private String formatDday(LocalDate targetDate, String pastText) {
        if (targetDate == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), targetDate);
        return days > 0 ? "D-" + days : days == 0 ? "D-DAY" : pastText;
    }

    private String formatRegistrationDday(ConferenceSettingsResponse conference) {
        LocalDate today = LocalDate.now(clock);
        if (within(today, conference.getEarlyBirdStartDate(), conference.getEarlyBirdEndDate())
                || within(today, conference.getRegularStartDate(), conference.getRegularEndDate())) {
            return "OPEN";
        }

        LocalDate nextStart = earlier(
                future(today, conference.getEarlyBirdStartDate()),
                future(today, conference.getRegularStartDate()));
        if (nextStart != null) {
            return formatDday(nextStart, "OPEN");
        }
        return conference.getEarlyBirdStartDate() != null || conference.getEarlyBirdEndDate() != null
                || conference.getRegularStartDate() != null || conference.getRegularEndDate() != null
                ? "CLOSED" : null;
    }

    private LocalDate future(LocalDate today, LocalDate date) {
        return date != null && today.isBefore(date) ? date : null;
    }

    private LocalDate earlier(LocalDate first, LocalDate second) {
        return first == null ? second : second == null || first.isBefore(second) ? first : second;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeRoutePath(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return normalizeRoutePath(requestPath);
    }

    private static String normalizeRoutePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return normalized.length() > 1 ? normalized.replaceAll("/+$", "") : normalized;
    }

    private String canonicalUrl(HttpServletRequest request) {
        return siteOrigin(request) + normalizeRoutePath(request);
    }

    private String siteOrigin(HttpServletRequest request) {
        String defaultPort = "http".equals(request.getScheme()) ? "80" : "443";
        String port = Integer.toString(request.getServerPort());
        return request.getScheme() + "://" + request.getServerName()
                + (defaultPort.equals(port) ? "" : ":" + port);
    }

}
