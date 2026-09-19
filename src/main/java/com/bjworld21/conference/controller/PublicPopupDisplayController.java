package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.service.ConferenceSettingsService;
import com.bjworld21.conference.service.PublicPopupService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import com.bjworld21.conference.publicsite.PublicSiteContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@IpAccessExempt
public class PublicPopupDisplayController {
    private final ConferenceSettingsService conferences;
    private final PublicPopupService popups;

    public PublicPopupDisplayController(ConferenceSettingsService conferences, PublicPopupService popups) {
        this.conferences = conferences;
        this.popups = popups;
    }

    @GetMapping(value = "/api/public/{conferenceSeq}/popups/display", produces = MediaType.TEXT_HTML_VALUE)
    public String display(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        // Explicit viewing does not clear or renew the daily suppression cookie.
        var site = PublicSiteContext.from(request);
        var display = popups.forManualOpen(site);
        if (display == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        }
        model.addAttribute("siteContext", site);
        model.addAttribute("language", site.language());
        model.addAttribute("popupDisplay", display);
        return "public/popups :: panel";
    }
}
