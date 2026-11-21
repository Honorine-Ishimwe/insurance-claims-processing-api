package com.claims.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the first administrator, read from BOOTSTRAP_ADMIN_* environment variables.
 * Used only when no ADMIN account exists yet. Remove the variables after the first start.
 */
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password, String fullName) {
}
