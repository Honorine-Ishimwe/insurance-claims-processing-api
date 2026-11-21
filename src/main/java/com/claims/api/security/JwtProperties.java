package com.claims.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret     Base64-encoded HMAC key, at least 256 bits (32 bytes) once decoded
 * @param ttlMinutes how long an issued token stays valid
 * @param issuer     value of the "iss" claim
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long ttlMinutes, String issuer) {
}
