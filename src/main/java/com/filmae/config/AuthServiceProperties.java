package com.filmae.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth-service")
public record AuthServiceProperties(
    @DefaultValue("http://localhost:8081") String baseUrl,
    @DefaultValue("3s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout
) {
}
