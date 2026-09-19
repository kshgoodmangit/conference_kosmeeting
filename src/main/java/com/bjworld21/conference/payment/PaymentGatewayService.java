package com.bjworld21.conference.payment;

import com.bjworld21.conference.config.PaymentGatewayProperties;
import com.bjworld21.conference.dto.PaymentCheckoutRequest;
import com.bjworld21.conference.dto.PaymentCheckoutResponse;
import com.bjworld21.conference.dto.PaymentGatewaySummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentGatewayService {
    private final PaymentGatewayProperties properties;
    private final PaymentGatewayRegistry registry;

    public PaymentGatewayService(PaymentGatewayProperties properties, PaymentGatewayRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    public PaymentGatewaySummaryResponse summary() {
        return registry.find(properties.getProvider())
                .map(PaymentGatewayAdapter::summary)
                .orElseGet(() -> new PaymentGatewaySummaryResponse(
                        false,
                        normalizeProvider(),
                        normalizeProvider(),
                        "application.yaml에 설정된 PG사 구현체를 찾을 수 없습니다.",
                        List.of()
                ));
    }

    public PaymentCheckoutResponse prepareCheckout(PaymentCheckoutRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("결제 기능이 비활성화되어 있습니다. app.payment.enabled 설정을 확인해주세요.");
        }

        PaymentGatewayAdapter adapter = registry.find(properties.getProvider())
                .orElseThrow(() -> new IllegalStateException(
                        "설정된 PG사 구현체를 찾을 수 없습니다: " + normalizeProvider()
                ));
        return adapter.prepareCheckout(request);
    }

    private String normalizeProvider() {
        String provider = properties.getProvider();
        return provider == null || provider.isBlank() ? "none" : provider.trim().toLowerCase();
    }
}
