package com.bjworld21.congress.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.maintenance-request.mail")
public class MaintenanceMailProperties {
    private boolean enabled = true;
    private String host = "smtp.gmail.com";
    private int port = 587;
    private String username = "bjworld21master@gmail.com";
    private String password = "";
    private String fromAddress = "bjworld21master@gmail.com";
    private String fromName = "ICMS 유지보수 요청";
    private String subjectPrefix = "[유지보수 요청]";
    private boolean authEnabled = true;
    private boolean starttlsEnabled = true;
    private int connectionTimeoutMillis = 5000;
    private int readTimeoutMillis = 10000;
    private int writeTimeoutMillis = 10000;
}
