package com.bjworld21.conference.dto;

import java.util.List;

public record PaymentGatewaySummaryResponse(
        boolean enabled,
        String provider,
        String providerDisplayName,
        String message,
        List<RouteSummary> routes
) {
    public record RouteSummary(
            String route,
            String label,
            boolean configured,
            String merchantIdHint,
            String payMethod,
            String currency,
            String language
    ) {
    }
}
