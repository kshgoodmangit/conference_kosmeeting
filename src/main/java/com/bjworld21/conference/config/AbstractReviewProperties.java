package com.bjworld21.conference.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.abstract-review")
public class AbstractReviewProperties {
    private boolean showAuthorInformation = false;
}
