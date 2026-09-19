package com.bjworld21.conference.payment;

import com.bjworld21.conference.config.PaymentGatewayProperties;
import com.bjworld21.conference.payment.paygate.PayGateProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PayGatePropertiesBindingTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertyConfiguration.class)
            .withPropertyValues(
                    "app.payment.enabled=true",
                    "app.payment.provider=paygate",
                    "app.payment.providers.paygate.production-script-url=https://api.example/paygate.js",
                    "app.payment.providers.paygate.routes.domestic-card.merchant-id=domestic-mid",
                    "app.payment.providers.paygate.routes.domestic-card.pay-method=card",
                    "app.payment.providers.paygate.routes.domestic-card.currency=KRW",
                    "app.payment.providers.paygate.routes.domestic-card.language=KR",
                    "app.payment.providers.paygate.routes.domestic-card.parameters.cardquota=00"
            );

    @Test
    void bindsCommonAndPayGateSettingsToSeparateClasses() {
        contextRunner.run(context -> {
            PaymentGatewayProperties common = context.getBean(PaymentGatewayProperties.class);
            PayGateProperties payGate = context.getBean(PayGateProperties.class);

            assertThat(common.isEnabled()).isTrue();
            assertThat(common.getProvider()).isEqualTo("paygate");
            assertThat(payGate.getProductionScriptUrl()).isEqualTo("https://api.example/paygate.js");
            assertThat(payGate.getRoutes().get("domestic-card").getMerchantId()).isEqualTo("domestic-mid");
            assertThat(payGate.getRoutes().get("domestic-card").getParameters())
                    .containsEntry("cardquota", "00");
        });
    }

    @EnableConfigurationProperties({PaymentGatewayProperties.class, PayGateProperties.class})
    static class PropertyConfiguration {
    }
}
