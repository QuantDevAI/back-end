package com.filmae.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    @DefaultValue("auth-service") String issuer
) {
}
