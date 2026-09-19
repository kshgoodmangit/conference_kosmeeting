package com.bjworld21.conference.publicsite;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** Immutable, request-scoped conference identity. Never populated from a session's last active site. */
public record PublicSiteContext(long conferenceSeq, String sitePath, String language,
                                List<String> supportedLanguages, String siteBasePath, String apiBasePath) {
    public static final String ATTRIBUTE = PublicSiteContext.class.getName();
    public static final String PAGE_PATH_ATTRIBUTE = ATTRIBUTE + ".pagePath";

    public PublicSiteContext {
        supportedLanguages = List.copyOf(supportedLanguages);
    }

    public static PublicSiteContext from(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof PublicSiteContext context) return context;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conference context is missing.");
    }

    public long getConferenceSeq() { return conferenceSeq; }
    public String getSitePath() { return sitePath; }
    public String getLanguage() { return language; }
    public List<String> getSupportedLanguages() { return supportedLanguages; }
    public String getSiteBasePath() { return siteBasePath; }
    public String getApiBasePath() { return apiBasePath; }

    public String pageUrl(String value) {
        if (value == null || value.isBlank()) return siteBasePath + "/";
        if (value.startsWith("#") || value.startsWith("//") || value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) return value;
        if (value.startsWith("/api/") || value.startsWith("/public/speakers/")) return apiUrl(value);
        if (value.startsWith("/public/") || value.startsWith("/assets/") || value.startsWith("/vendor/")) return value;
        String path = value.startsWith("/") ? value : "/" + value;
        if (!siteBasePath.isEmpty() && (path.equals(siteBasePath) || path.startsWith(siteBasePath + "/") || path.startsWith(siteBasePath + "?"))) return path;
        return siteBasePath + path;
    }

    public String apiUrl(String value) {
        String path = value == null ? "" : value;
        if (path.startsWith("/public/speakers/")) path = "/speakers/" + path.substring("/public/speakers/".length());
        if (path.startsWith("/api/public/")) {
            path = path.substring("/api/public/".length());
            path = path.replaceFirst("^[0-9]+(?=/|\\?|$)", "");
        } else if (path.startsWith("/api/")) {
            if (path.startsWith("/api/boards/images/") || path.startsWith("/api/popups/images/")
                    || path.startsWith("/api/mail/images/") || path.startsWith("/api/countries/")
                    || path.startsWith("/api/security/")) return path;
            path = path.substring("/api".length());
        }
        if (!path.startsWith("/")) path = "/" + path;
        return UriComponentsBuilder.fromUriString(apiBasePath + path)
                .replaceQueryParam("lang", language).build().toUriString();
    }
}
