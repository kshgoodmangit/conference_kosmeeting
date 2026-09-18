package com.bjworld21.congress.mail;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class MailWebhookRegistry {
    private final Map<String, MailWebhookAdapter> adapters;

    public MailWebhookRegistry(List<MailWebhookAdapter> adapters) {
        Map<String, MailWebhookAdapter> registered = new LinkedHashMap<>();
        for (MailWebhookAdapter adapter : adapters) {
            String name = adapter.providerName().toLowerCase(Locale.ROOT);
            if (registered.putIfAbsent(name, adapter) != null) {
                throw new IllegalStateException("중복된 메일 Webhook 공급자 어댑터입니다: " + name);
            }
        }
        this.adapters = Map.copyOf(registered);
    }

    public Optional<MailWebhookAdapter> find(String provider) {
        return Optional.ofNullable(adapters.get(provider.toLowerCase(Locale.ROOT)));
    }
}
