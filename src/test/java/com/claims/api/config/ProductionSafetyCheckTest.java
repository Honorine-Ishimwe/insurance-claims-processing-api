package com.claims.api.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.claims.api.security.JwtProperties;

class ProductionSafetyCheckTest {

    @Test
    @DisplayName("fails fast if prod is combined with dev")
    void rejectsProdWithDev() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.profiles.active", "prod,dev");
        env.setActiveProfiles("prod", "dev");
        ProductionSafetyCheck check = new ProductionSafetyCheck(env, new JwtProperties("c3VwZXItc2VjcmV0LXByb2Qta2V5LXRoYXQtaXMtbm90LXB1YmxpYw==", 60, "claims-api"));

        assertThatThrownBy(check::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be combined");
    }

    @Test
    @DisplayName("fails fast if prod uses a publicly known dev/test JWT secret")
    void rejectsKnownPublicSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyCheck check = new ProductionSafetyCheck(env, new JwtProperties(
                "ZGV2LW9ubHktand0LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2R1Y3Rpb24tZXZlci0xMjM=", 60, "claims-api"));

        assertThatThrownBy(check::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicly known");
    }

    @Test
    @DisplayName("passes with prod profile and a unique secret")
    void allowsValidProdConfig() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyCheck check = new ProductionSafetyCheck(env, new JwtProperties(
                "c3VwZXItc2VjcmV0LXByb2Qta2V5LXRoYXQtaXMtbm90LXB1YmxpYw==", 60, "claims-api"));

        assertThatCode(check::verify).doesNotThrowAnyException();
    }
}
