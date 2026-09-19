package com.bjworld21.conference.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.member-password-reset")
public class MemberPasswordResetProperties {
    private boolean enabled = true;
    private String publicBaseUrl = "http://localhost:8080";
    private Duration tokenTtl = Duration.ofMinutes(30);

    public String validatedBaseUrl() {
        URI uri;
        try {
            uri = URI.create(publicBaseUrl == null ? "" : publicBaseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid password recovery configuration");
        }
        boolean local = "http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if (uri.getHost() == null || (!"https".equals(uri.getScheme()) && !local)
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || tokenTtl == null || tokenTtl.compareTo(Duration.ofMinutes(1)) < 0
                || tokenTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("Invalid password recovery configuration");
        }
        return uri.toString().replaceAll("/+$", "");
    }
}
