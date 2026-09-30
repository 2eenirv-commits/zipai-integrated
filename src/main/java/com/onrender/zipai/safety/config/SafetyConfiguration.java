package com.onrender.zipai.safety.config;

import com.onrender.zipai.safety.service.SafetyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SafetyProperties.class)
public class SafetyConfiguration {
}
