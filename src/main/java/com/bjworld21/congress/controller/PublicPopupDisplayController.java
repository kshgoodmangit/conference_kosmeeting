package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.IpAccessExempt;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.bjworld21.congress.service.PublicPopupService;
import jakarta.servlet.http.HttpServletResponse;
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

    @GetMapping(value = "/popups/display", produces = MediaType.TEXT_HTML_VALUE)
    public String display(HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        // Explicit viewing does not clear or renew the daily suppression cookie.
        var display = popups.forManualOpen(conferences.getLatestConferenceSeq());
        if (display == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        }
        model.addAttribute("popupDisplay", display);
        return "public/popups :: panel";
    }
}
