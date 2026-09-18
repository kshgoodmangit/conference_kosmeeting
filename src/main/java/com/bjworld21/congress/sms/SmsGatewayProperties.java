package com.bjworld21.congress.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data @Component @ConfigurationProperties(prefix = "app.sms")
public class SmsGatewayProperties {
    private boolean enabled = false;
    private String provider = "none";
}
