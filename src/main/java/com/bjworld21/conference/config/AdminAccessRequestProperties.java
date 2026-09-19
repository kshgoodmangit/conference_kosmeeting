package com.bjworld21.conference.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.admin-access-request")
public class AdminAccessRequestProperties {
    private int cooldownMinutes = 10;
    private int dailyLimit = 3;
}
