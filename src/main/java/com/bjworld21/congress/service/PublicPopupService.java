package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.PublicPopupDisplay;
import com.bjworld21.congress.entity.Popup;
import com.bjworld21.congress.repository.PopupRepository;
import com.bjworld21.congress.publicsite.PublicSiteContext;
import com.bjworld21.congress.publicsite.PublicContentLinks;
import jakarta.servlet.http.Cookie;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class PublicPopupService {
    private static final Logger log = LoggerFactory.getLogger(PublicPopupService.class);
    private final PopupRepository repository;
    private final PopupLayoutSettingsService layouts;
    private final PublicPopupPreferences preferences;
    private final CmsHtmlSanitizer sanitizer;
    private final Clock clock;

    public PublicPopupService(PopupRepository repository, PopupLayoutSettingsService layouts,
                              PublicPopupPreferences preferences, CmsHtmlSanitizer sanitizer, Clock clock) {
        this.repository = repository;
        this.layouts = layouts;
        this.preferences = preferences;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    public PublicPopupDisplay forHome(Long conferenceSeq, Cookie[] cookies) {
        // Check before either query: suppressed popups must never enter the page model.
        if (preferences.isHidden(conferenceSeq, cookies)) return null;
        return forManualOpen(conferenceSeq);
    }

    public PublicPopupDisplay forHome(PublicSiteContext site, Cookie[] cookies) {
        return withSiteLinks(forHome(site.conferenceSeq(), cookies), site);
    }

    public PublicPopupDisplay forManualOpen(PublicSiteContext site) {
        return withSiteLinks(forManualOpen(site.conferenceSeq()), site);
    }

    private PublicPopupDisplay withSiteLinks(PublicPopupDisplay display, PublicSiteContext site) {
        if (display == null) return null;
        return new PublicPopupDisplay(display.layoutNo(), display.items().stream().map(item ->
                new PublicPopupDisplay.Item(item.seq(), item.title(), PublicContentLinks.render(item.contentHtml(), site),
                        item.imageUrl() == null ? null : site.apiUrl(item.imageUrl()),
                        item.linkUrl() == null ? null : site.pageUrl(item.linkUrl()))).toList());
    }

    public PublicPopupDisplay forManualOpen(Long conferenceSeq) {
        try {
            var items = repository.findVisible(conferenceSeq,
                            LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))))
                    .stream().map(this::toItem).filter(Objects::nonNull).toList();
            if (items.isEmpty()) return null;
            return new PublicPopupDisplay(layouts.getPopupLayoutNo(conferenceSeq), items);
        } catch (RuntimeException exception) {
            log.warn("Unable to load public popups for conference {}", conferenceSeq, exception);
            return null;
        }
    }

    private PublicPopupDisplay.Item toItem(Popup popup) {
        var document = Jsoup.parseBodyFragment(sanitizer.sanitize(popup.getContent()));
        // Keep CMS classes from colliding with the public site's navigation and controls.
        document.select("[class]").forEach(element -> element.removeAttr("class"));
        document.select("iframe").forEach(element -> element.attr("loading", "lazy"));
        document.select("img").forEach(element -> {
            element.attr("loading", "lazy");
            if (element.attr("alt").isBlank()) element.attr("alt", popup.getTitle());
        });
        boolean hasContent = !document.text().replace('\u00a0', ' ').isBlank()
                || !document.select("img[src], iframe[src]").isEmpty();
        String imageUrl = popup.getPopupImageSaveFilename() == null || popup.getPopupImageSaveFilename().isBlank()
                ? null : "/api/public/" + popup.getConferenceSeq() + "/popups/" + popup.getSeq() + "/image";
        if (!hasContent && imageUrl == null) return null;

        // Apply the same URL policy to legacy standalone links as to editor links.
        var link = new org.jsoup.nodes.Element("a").attr("href",
                popup.getLinkUrl() == null ? "" : popup.getLinkUrl().trim()).text("Details");
        String safeLink = Jsoup.parseBodyFragment(sanitizer.sanitize(link.outerHtml())).select("a[href]").attr("href");
        return new PublicPopupDisplay.Item(popup.getSeq(), popup.getTitle(),
                hasContent ? document.body().html() : "", hasContent ? null : imageUrl,
                safeLink.isBlank() ? null : safeLink);
    }
}
