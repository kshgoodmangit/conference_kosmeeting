package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.IpAccessExempt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationInfoController {
    private final String applicationName;

    public ApplicationInfoController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    public record ApplicationInfo(String applicationName) {}

    @GetMapping("/api/application-info")
    @IpAccessExempt
    public ResponseEntity<ApplicationInfo> getApplicationInfo() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApplicationInfo(applicationName));
    }
}
