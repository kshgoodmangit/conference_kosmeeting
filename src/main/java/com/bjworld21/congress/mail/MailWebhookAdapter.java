package com.bjworld21.congress.mail;

import com.bjworld21.congress.dto.NormalizedMailEvent;
import org.springframework.http.HttpHeaders;

import java.util.List;

public interface MailWebhookAdapter {
    String providerName();

    boolean verify(HttpHeaders headers, byte[] rawBody);

    List<NormalizedMailEvent> parse(HttpHeaders headers, byte[] rawBody);
}
