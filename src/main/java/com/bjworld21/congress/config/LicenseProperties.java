package com.bjworld21.congress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.license")
public class LicenseProperties {
    private boolean conferenceCreationEnabled = false;
    private boolean abstractSimilarityEnabled = false;
    private boolean eventDashboardEnabled = false;
    private boolean userAnalyticsDashboardEnabled = false;
}
