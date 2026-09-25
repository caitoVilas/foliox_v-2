package com.foliox.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "foliox.auth")
public record AuthProperties(String issuer, Duration tokenTtl) {
}
