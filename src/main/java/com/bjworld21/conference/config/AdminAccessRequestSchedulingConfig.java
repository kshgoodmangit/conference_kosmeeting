package com.bjworld21.conference.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Keep request mail scheduling independent of temporary dashboard test-data jobs. */
@Configuration
@EnableScheduling
public class AdminAccessRequestSchedulingConfig {
}
