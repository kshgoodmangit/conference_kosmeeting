package com.bjworld21.congress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.admin-ip-access")
public class AdminIpAccessProperties {
    private boolean enabled = false;
    private List<String> trustedProxies = new ArrayList<>();
}
