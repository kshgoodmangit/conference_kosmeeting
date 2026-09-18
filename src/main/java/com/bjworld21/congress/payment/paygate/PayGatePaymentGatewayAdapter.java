package com.bjworld21.congress.payment.paygate;

import com.bjworld21.congress.config.PaymentGatewayProperties;
import com.bjworld21.congress.dto.PaymentCheckoutRequest;
import com.bjworld21.congress.dto.PaymentCheckoutResponse;
import com.bjworld21.congress.dto.PaymentGatewaySummaryResponse;
import com.bjworld21.congress.payment.PaymentGatewayAdapter;
import com.bjworld21.congress.payment.PaymentRoute;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class PayGatePaymentGatewayAdapter implements PaymentGatewayAdapter {
    private static final String PROVIDER_CODE = "paygate";
    private static final String PROVIDER_DISPLAY_NAME = "PayGate";
    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<String> RESULT_FIELDS = List.of(
            "replycode",
            "replyMsg",
            "tid",
            "cardauthcode",
            "cardnumber",
            "cardtype",
            "ResultScreen"
    );

    private final PaymentGatewayProperties commonProperties;
    private final PayGateProperties payGateProperties;

    public PayGatePaymentGatewayAdapter(
            PaymentGatewayProperties commonProperties,
            PayGateProperties payGateProperties
    ) {
        this.commonProperties = commonProperties;
        this.payGateProperties = payGateProperties;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public PaymentGatewaySummaryResponse summary() {
        List<PaymentGatewaySummaryResponse.RouteSummary> routes = new ArrayList<>();

        for (PaymentRoute route : PaymentRoute.values()) {
            PayGateProperties.RouteProperties routeProperties = payGateProperties.getRoutes().get(route.configKey());
            routes.add(new PaymentGatewaySummaryResponse.RouteSummary(
                    route.configKey(),
                    route.label(),
                    isRouteConfigured(routeProperties),
                    merchantIdHint(routeProperties == null ? null : routeProperties.getMerchantId()),
                    routeProperties == null ? "" : trim(routeProperties.getPayMethod()),
                    routeProperties == null ? "" : normalizeCurrency(routeProperties.getCurrency()),
                    routeProperties == null ? "" : trim(routeProperties.getLanguage()).toUpperCase(Locale.ROOT)
            ));
        }

        boolean enabled = commonProperties.isEnabled();
        String message = enabled
                ? "PayGate 결제 모듈이 활성화되어 있습니다."
                : "PayGate 설정은 로드되었지만 결제 기능은 비활성화되어 있습니다.";

        return new PaymentGatewaySummaryResponse(
                enabled,
                PROVIDER_CODE,
                PROVIDER_DISPLAY_NAME,
                message,
                routes
        );
    }

    @Override
    public PaymentCheckoutResponse prepareCheckout(PaymentCheckoutRequest request) {
        validateMemberType(request.getMemberType());
        PaymentRoute route = PaymentRoute.from(request.getRoute());

        PayGateProperties.RouteProperties routeProperties = payGateProperties.getRoutes().get(route.configKey());
        if (!isRouteConfigured(routeProperties)) {
            throw new IllegalStateException(route.label() + " PayGate 설정이 완료되지 않았습니다.");
        }

        BigDecimal amount = request.getAmount().stripTrailingZeros();
        String currency = normalizeCurrency(routeProperties.getCurrency());
        if ("KRW".equals(currency) && amount.scale() > 0) {
            throw new IllegalArgumentException("원화 결제금액에는 소수점을 사용할 수 없습니다.");
        }

        String orderNumber = trim(request.getOrderNumber());
        if (orderNumber.isEmpty()) {
            orderNumber = generateOrderNumber();
        }

        Map<String, String> fields = new LinkedHashMap<>(routeProperties.getParameters());
        fields.put("mid", trim(routeProperties.getMerchantId()));
        fields.put("paymethod", trim(routeProperties.getPayMethod()));
        fields.put("goodname", request.getProductName().trim());
        fields.put("unitprice", amount.toPlainString());
        fields.put("goodcurrency", toPayGateCurrency(currency));
        fields.put("langcode", trim(routeProperties.getLanguage()).toUpperCase(Locale.ROOT));
        fields.put("mb_serial_no", orderNumber);
        fields.put("charset", "UTF-8");
        fields.putIfAbsent("cardquota", "00");
        putIfPresent(fields, "receipttoname", request.getBuyerName());
        putIfPresent(fields, "receipttoemail", request.getBuyerEmail());
        RESULT_FIELDS.forEach(field -> fields.putIfAbsent(field, ""));

        return new PaymentCheckoutResponse(
                PROVIDER_CODE,
                PROVIDER_DISPLAY_NAME,
                route.configKey(),
                scriptUrl(),
                "PGIOForm",
                "PGIOscreen",
                orderNumber,
                currency,
                request.getAmount(),
                Map.copyOf(fields),
                RESULT_FIELDS,
                "테스트 화면은 결제 결과를 표시만 하며 사전등록 결제 상태를 변경하지 않습니다."
        );
    }

    private boolean isRouteConfigured(PayGateProperties.RouteProperties route) {
        return route != null
                && !scriptUrl().isBlank()
                && !trim(route.getMerchantId()).isBlank()
                && !trim(route.getPayMethod()).isBlank()
                && !normalizeCurrency(route.getCurrency()).isBlank()
                && !trim(route.getLanguage()).isBlank();
    }

    private String scriptUrl() {
        return trim(payGateProperties.getProductionScriptUrl());
    }

    private static void validateMemberType(String memberType) {
        String value = trim(memberType).toLowerCase(Locale.ROOT);
        if (!"domestic".equals(value) && !"international".equals(value)) {
            throw new IllegalArgumentException("회원 유형은 domestic 또는 international이어야 합니다.");
        }
    }

    private static String normalizeCurrency(String currency) {
        return trim(currency).toUpperCase(Locale.ROOT);
    }

    private static String toPayGateCurrency(String currency) {
        return "KRW".equals(currency) ? "WON" : currency;
    }

    private static void putIfPresent(Map<String, String> fields, String name, String value) {
        String normalized = trim(value);
        if (!normalized.isEmpty()) {
            fields.put(name, normalized);
        }
    }

    private static String merchantIdHint(String merchantId) {
        String value = trim(merchantId);
        if (value.isEmpty()) {
            return "미설정";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private static String generateOrderNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "PGTEST-" + ORDER_TIME_FORMAT.format(LocalDateTime.now()) + "-" + suffix;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
