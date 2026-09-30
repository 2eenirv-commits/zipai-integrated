package com.onrender.zipai.safety.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zipai.safety")
public record SafetyProperties(
    String vworldApiKey,
    int defaultRadiusMeters,
    int maxRadiusMeters
) {
    public SafetyProperties {
        if (defaultRadiusMeters <= 0) defaultRadiusMeters = 500;
        if (maxRadiusMeters <= 0) maxRadiusMeters = 2000;
    }
}
