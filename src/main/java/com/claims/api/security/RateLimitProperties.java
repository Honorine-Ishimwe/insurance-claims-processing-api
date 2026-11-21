package com.claims.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled       turn the limiter on/off
 * @param maxRequests   attempts allowed per client IP per window
 * @param windowSeconds window length
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, int maxRequests, int windowSeconds) {
}
