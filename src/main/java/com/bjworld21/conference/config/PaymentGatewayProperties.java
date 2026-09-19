package com.bjworld21.conference.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment")
public class PaymentGatewayProperties {
    private boolean enabled;
    private String provider = "none";
}
