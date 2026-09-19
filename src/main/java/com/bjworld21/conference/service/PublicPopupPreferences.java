package com.bjworld21.conference.service;

import jakarta.servlet.http.Cookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;

@Service
public class PublicPopupPreferences {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public PublicPopupPreferences(Clock clock) {
        this.clock = clock;
    }

    public boolean isHidden(Long conferenceSeq, Cookie[] cookies) {
        String today = clock.instant().atZone(ZONE).toLocalDate().toString();
        return cookies != null && Arrays.stream(cookies).anyMatch(cookie ->
                cookieName(conferenceSeq).equals(cookie.getName()) && today.equals(cookie.getValue()));
    }

    public ResponseCookie hideToday(Long conferenceSeq, boolean secure) {
        var now = clock.instant().atZone(ZONE);
        var midnight = now.toLocalDate().plusDays(1).atStartOfDay(ZONE);
        Duration remaining = Duration.between(now, midnight);
        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        return ResponseCookie.from(cookieName(conferenceSeq), now.toLocalDate().toString())
                .path("/").httpOnly(true).secure(secure).sameSite("Lax").maxAge(seconds).build();
    }

    private String cookieName(Long conferenceSeq) {
        return "conference_popups_hidden_" + conferenceSeq;
    }
}
