package com.bjworld21.conference.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PaymentCheckoutResponse(
        String provider,
        String providerDisplayName,
        String route,
        String scriptUrl,
        String formName,
        String screenElementId,
        String orderNumber,
        String currency,
        BigDecimal amount,
        Map<String, String> fields,
        List<String> resultFieldNames,
        String notice
) {
}
