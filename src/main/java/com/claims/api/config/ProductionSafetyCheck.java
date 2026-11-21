package com.claims.api.config;

import java.util.Arrays;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.claims.api.security.JwtProperties;

import jakarta.annotation.PostConstruct;

/**
 * Fails fast at startup if the "prod" profile is running with development settings:
 * the dev profile (demo users) or a publicly known JWT key. A misconfigured production
 * deploy should crash loudly, not run insecurely.
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    /** Keys committed to this repository for dev/test. Anyone can read them, so they can forge tokens. */
    private static final Set<String> KNOWN_PUBLIC_SECRETS = Set.of(
            "ZGV2LW9ubHktand0LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2R1Y3Rpb24tZXZlci0xMjM=",
            "dGVzdC1vbmx5LWp3dC1zZWNyZXQtZm9yLXVuaXQtYW5kLWludGVncmF0aW9uLXRlc3Rz",
            "/RIZgU9NvQPazgPk0A6L4yUWEShEPMmrpvO0LUb2uKp4QuUBkazGnkkSgrxCoUau");

    private final Environment environment;
    private final JwtProperties jwt;

    public ProductionSafetyCheck(Environment environment, JwtProperties jwt) {
        this.environment = environment;
        this.jwt = jwt;
    }

    @PostConstruct
    void verify() {
        Set<String> active = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        if (active.contains("dev") || active.contains("test")) {
            throw new IllegalStateException("Refusing to start: the 'prod' profile cannot be combined with "
                    + active + ". Demo data and dev defaults must never run in production.");
        }
        if (jwt.secret() != null && KNOWN_PUBLIC_SECRETS.contains(jwt.secret().trim())) {
            throw new IllegalStateException("Refusing to start: JWT_SECRET is a publicly known development key. "
                    + "Generate a new one with: openssl rand -base64 48");
        }
    }
}
