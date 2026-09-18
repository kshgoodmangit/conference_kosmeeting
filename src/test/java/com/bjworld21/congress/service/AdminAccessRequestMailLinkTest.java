package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.entity.AdminAccessRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.*;

import static org.assertj.core.api.Assertions.*;

class AdminAccessRequestMailLinkTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final PersonalDataProperties personal = PersonalDataTestSupport.properties();
    private final AdminAccessRequestMailLink links = new AdminAccessRequestMailLink(personal, clock);

    @Test void signedLinkAcceptsOnlyItsRequestAndExpiry() {
        var request = new AdminAccessRequest();
        request.setSeq(3L);
        request.setSiteUrl("https://conference.example");
        request.setExpiresAt(LocalDateTime.now(clock).plusHours(24));
        String url = links.create(request);
        var params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        long expires = Long.parseLong(params.getFirst("expires"));
        String signature = params.getFirst("signature");
        assertThat(url).startsWith("https://conference.example/admin/access-request-approval?seq=3&");
        assertThatCode(() -> links.verify(3L, expires, signature)).doesNotThrowAnyException();
        assertThatThrownBy(() -> links.verify(4L, expires, signature)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> links.verify(3L, expires + 1, signature)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> links.verify(3L, expires, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> links.verify(3L, expires, "x".repeat(43))).isInstanceOf(ResponseStatusException.class);
        var expired = new AdminAccessRequestMailLink(personal, Clock.offset(clock, Duration.ofHours(24)));
        assertThatThrownBy(() -> expired.verify(3L, expires, signature)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(410));
    }
}
