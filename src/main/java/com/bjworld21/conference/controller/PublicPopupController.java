package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import com.bjworld21.conference.publicsite.PublicApiRequest;
import com.bjworld21.conference.service.ConferenceSettingsService;
import com.bjworld21.conference.service.PublicPopupPreferences;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{conferenceSeq}/popups")
@IpAccessExempt
public class PublicPopupController {
    private final ConferenceSettingsService conferences;
    private final PublicPopupPreferences preferences;

    public PublicPopupController(ConferenceSettingsService conferences, PublicPopupPreferences preferences) {
        this.conferences = conferences;
        this.preferences = preferences;
    }

    @PostMapping("/dismiss-today")
    public ResponseEntity<Void> dismissToday(HttpServletRequest request) {
        var cookie = preferences.hideToday(PublicApiRequest.context().conferenceSeq(), request.isSecure());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }
}
