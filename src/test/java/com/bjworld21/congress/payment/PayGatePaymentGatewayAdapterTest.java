package com.bjworld21.congress.payment;

import com.bjworld21.congress.config.PaymentGatewayProperties;
import com.bjworld21.congress.dto.PaymentCheckoutRequest;
import com.bjworld21.congress.dto.PaymentCheckoutResponse;
import com.bjworld21.congress.dto.PaymentGatewaySummaryResponse;
import com.bjworld21.congress.payment.paygate.PayGatePaymentGatewayAdapter;
import com.bjworld21.congress.payment.paygate.PayGateProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayGatePaymentGatewayAdapterTest {
    private PaymentGatewayProperties properties;
    private PayGateProperties payGateProperties;
    private PayGatePaymentGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new PaymentGatewayProperties();
        properties.setEnabled(true);
        properties.setProvider("paygate");

        payGateProperties = new PayGateProperties();
        payGateProperties.setProductionScriptUrl("https://api.paygate.net/ajax/common/OpenPayAPI.js");

        Map<String, PayGateProperties.RouteProperties> routes = new LinkedHashMap<>();
        routes.put("domestic-card", route("domestic-mid", "card", "KRW", "KR"));
        routes.put("international-card", route("international-mid", "104", "USD", "US"));
        payGateProperties.setRoutes(routes);

        adapter = new PayGatePaymentGatewayAdapter(properties, payGateProperties);
    }

    @Test
    void preparesDomesticCheckoutFromYamlRoute() {
        PaymentCheckoutRequest request = request("domestic", "domestic-card", "1000");

        PaymentCheckoutResponse response = adapter.prepareCheckout(request);

        assertThat(response.provider()).isEqualTo("paygate");
        assertThat(response.scriptUrl()).isEqualTo("https://api.paygate.net/ajax/common/OpenPayAPI.js");
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.fields())
                .containsEntry("mid", "domestic-mid")
                .containsEntry("paymethod", "card")
                .containsEntry("goodcurrency", "WON")
                .containsEntry("langcode", "KR")
                .containsEntry("unitprice", "1000")
                .containsEntry("cardquota", "00")
                .containsEntry("receipttoname", "Tester")
                .containsEntry("receipttoemail", "tester@example.com")
                .doesNotContainKeys("language", "receiptname", "receiptemail", "receipttotel", "receipttel");
    }

    @Test
    void preparesInternationalCheckoutWithUsdAnd3dSecureRoute() {
        PaymentCheckoutRequest request = request("international", "international-card", "1.25");

        PaymentCheckoutResponse response = adapter.prepareCheckout(request);

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.fields())
                .containsEntry("mid", "international-mid")
                .containsEntry("paymethod", "104")
                .containsEntry("goodcurrency", "USD")
                .containsEntry("langcode", "US")
                .containsEntry("unitprice", "1.25")
                .containsEntry("receipttoname", "Tester")
                .doesNotContainKeys("language", "receiptname");
    }

    @Test
    void rejectsFractionalKrwAmount() {
        PaymentCheckoutRequest request = request("domestic", "domestic-card", "1000.50");

        assertThatThrownBy(() -> adapter.prepareCheckout(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("원화 결제금액");
    }

    @Test
    void summaryDoesNotExposeFullMerchantId() {
        PaymentGatewaySummaryResponse summary = adapter.summary();

        assertThat(summary.routes()).extracting(PaymentGatewaySummaryResponse.RouteSummary::merchantIdHint)
                .containsExactly("do****id", "in****id");
    }

    private PayGateProperties.RouteProperties route(
            String merchantId,
            String payMethod,
            String currency,
            String language
    ) {
        PayGateProperties.RouteProperties route = new PayGateProperties.RouteProperties();
        route.setMerchantId(merchantId);
        route.setPayMethod(payMethod);
        route.setCurrency(currency);
        route.setLanguage(language);
        route.setParameters(Map.of("cardquota", "00"));
        return route;
    }

    private PaymentCheckoutRequest request(String memberType, String route, String amount) {
        PaymentCheckoutRequest request = new PaymentCheckoutRequest();
        request.setMemberType(memberType);
        request.setRoute(route);
        request.setAmount(new BigDecimal(amount));
        request.setProductName("ICMS 2026 registration");
        request.setBuyerName("Tester");
        request.setBuyerEmail("tester@example.com");
        request.setBuyerPhone("01012345678");
        return request;
    }
}
