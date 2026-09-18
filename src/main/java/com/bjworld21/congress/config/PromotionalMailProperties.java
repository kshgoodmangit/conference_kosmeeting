package com.bjworld21.congress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.promotional-mail")
public class PromotionalMailProperties {
    private boolean enabled;
    private String provider = "none";
    private String publicBaseUrl = "";
    private long maxAttachmentTotalBytes = 10L * 1024L * 1024L;
    private int maxAttachmentCount = 5;
    private long maxContentImageBytes = 5L * 1024L * 1024L;
}
