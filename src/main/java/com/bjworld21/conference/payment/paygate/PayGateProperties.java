package com.bjworld21.conference.payment.paygate;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment.providers.paygate")
public class PayGateProperties {
    private String productionScriptUrl = "";
    private Map<String, RouteProperties> routes = new LinkedHashMap<>();

    @Data
    public static class RouteProperties {
        private String merchantId = "";
        private String payMethod = "";
        private String currency = "";
        private String language = "";
        private Map<String, String> parameters = new LinkedHashMap<>();
    }
}
