package com.bjworld21.conference.payment;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class PaymentGatewayRegistry {
    private final Map<String, PaymentGatewayAdapter> adapters;

    public PaymentGatewayRegistry(List<PaymentGatewayAdapter> adapters) {
        Map<String, PaymentGatewayAdapter> registered = new LinkedHashMap<>();
        for (PaymentGatewayAdapter adapter : adapters) {
            String providerCode = normalize(adapter.providerCode());
            if (registered.putIfAbsent(providerCode, adapter) != null) {
                throw new IllegalStateException("중복된 PG사 어댑터입니다: " + providerCode);
            }
        }
        this.adapters = Map.copyOf(registered);
    }

    public Optional<PaymentGatewayAdapter> find(String providerCode) {
        return Optional.ofNullable(adapters.get(normalize(providerCode)));
    }

    private static String normalize(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
    }
}
