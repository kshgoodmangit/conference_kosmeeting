package com.bjworld21.congress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.web-risk")
public class WebRiskProperties {
    private boolean enabled = true;
    private String apiKey = "";
    private String baseUrl = "https://webrisk.googleapis.com";
    private int connectTimeoutSeconds = 3;
    private int readTimeoutSeconds = 5;
}
