package com.bjworld21.conference.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.abstract-title-similarity")
public class AbstractTitleSimilarityProperties {
    private double warningThreshold = 85.0;
    private int maxMatches = 5;
}
