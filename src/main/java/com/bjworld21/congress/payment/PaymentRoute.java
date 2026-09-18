package com.bjworld21.congress.payment;

import java.util.Arrays;

public enum PaymentRoute {
    DOMESTIC_CARD("domestic-card", "국내발급 카드"),
    INTERNATIONAL_CARD("international-card", "해외발급 카드");

    private final String configKey;
    private final String label;

    PaymentRoute(String configKey, String label) {
        this.configKey = configKey;
        this.label = label;
    }

    public String configKey() {
        return configKey;
    }

    public String label() {
        return label;
    }

    public static PaymentRoute from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("카드 발급 구분이 필요합니다.");
        }

        return Arrays.stream(values())
                .filter(route -> route.configKey.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 카드 발급 구분입니다: " + value));
    }
}
