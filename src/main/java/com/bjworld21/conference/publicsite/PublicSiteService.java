package com.bjworld21.conference.publicsite;

import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.service.ConferenceSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class PublicSiteService {
    private static final Set<String> RESERVED = Set.of("api", "admin", "assets", "images", "public", "vendor", "commoncode", "error", "actuator");
    private final ConferenceSettingsService conferences;
    private final PublicSiteProperties properties;

    public PublicSiteService(ConferenceSettingsService conferences, PublicSiteProperties properties) {
        this.conferences = conferences;
        this.properties = properties;
    }

    public boolean isMulti() { return properties.isMulti(); }

    public List<ConferenceSettingsResponse> getPublishedConferences() {
        return conferences.getSettingsList().stream()
                .filter(conference -> Boolean.TRUE.equals(conference.getPublished()))
                .toList();
    }

    public PublicSiteContext resolveApi(long conferenceSeq, String language) {
        if (conferenceSeq <= 0 || (!properties.isMulti() && !Long.valueOf(conferenceSeq).equals(properties.getDefaultConferenceSeq()))) throw notFound();
        return context(requireConference(conferences.getSettings(conferenceSeq)), language);
    }

    public ResolvedPage resolvePage(String rawPath) {
        String path = rawPath == null || rawPath.isBlank() ? "/" : rawPath;
        if (!path.startsWith("/") || path.contains("//") || path.contains("%") || path.contains("\\") || path.contains(";") || path.contains("..")) throw notFound();
        List<String> segments = new ArrayList<>(List.of(path.substring(1).split("/")));
        segments.removeIf(String::isEmpty);
        if (!segments.isEmpty() && RESERVED.contains(segments.get(0))) throw notFound();
        ConferenceSettingsResponse conference;
        if (properties.isMulti()) {
            if (segments.isEmpty()) {
                var latest = getPublishedConferences().stream()
                        .max(Comparator.comparing(ConferenceSettingsResponse::getSeq));
                if (latest.isEmpty()) return new ResolvedPage(null, "/", null);
                PublicSiteContext site = context(latest.get(), null);
                return new ResolvedPage(site, "/", site.pageUrl("/"));
            }
            String sitePath = segments.remove(0);
            if (!sitePath.matches("[a-z0-9][a-z0-9_-]{0,99}")) throw notFound();
            conference = requireConference(conferences.getSettingsBySitePath(sitePath));
        } else {
            if (properties.getDefaultConferenceSeq() == null) throw notFound();
            conference = requireConference(conferences.getSettings(properties.getDefaultConferenceSeq()));
        }
        List<String> languages = languages(conference);
        String language = conference.getDefaultLanguage();
        if (!segments.isEmpty() && languages.contains(segments.get(0))) language = segments.remove(0);
        PublicSiteContext site = context(conference, language);
        String pagePath = segments.isEmpty() ? "/" : "/" + String.join("/", segments);
        if (!pagePath.equals("/") && !pagePath.matches("/[a-z0-9][a-z0-9-]*")) throw notFound();
        String canonical = site.siteBasePath() + pagePath;
        return new ResolvedPage(site, pagePath, canonical.equals(path) ? null : canonical);
    }

    private PublicSiteContext context(ConferenceSettingsResponse conference, String requestedLanguage) {
        if (!Boolean.TRUE.equals(conference.getPublished())) throw notFound();
        List<String> languages = languages(conference);
        String language = requestedLanguage == null || requestedLanguage.isBlank() ? conference.getDefaultLanguage() : requestedLanguage;
        if (!languages.contains(language)) throw notFound();
        String sitePath = conference.getSitePath();
        if (properties.isMulti() && (sitePath == null || !sitePath.matches("[a-z0-9][a-z0-9_-]{0,99}") || RESERVED.contains(sitePath))) throw notFound();
        String base = properties.isMulti() ? "/" + sitePath : "";
        if (languages.size() > 1) base += "/" + language;
        return new PublicSiteContext(conference.getSeq(), sitePath, language, languages, base, "/api/public/" + conference.getSeq());
    }

    private List<String> languages(ConferenceSettingsResponse conference) {
        List<String> languages = conference.getSupportedLanguages();
        if (languages == null || languages.isEmpty() || !languages.contains(conference.getDefaultLanguage())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Conference languages are not configured.");
        }
        return languages;
    }

    private ConferenceSettingsResponse requireConference(ConferenceSettingsResponse value) {
        if (value == null || value.getSeq() == null || !Boolean.TRUE.equals(value.getPublished())) throw notFound();
        return value;
    }

    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conference or language not found."); }

    public record ResolvedPage(PublicSiteContext context, String pagePath, String redirectUrl) { }
}
