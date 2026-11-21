package com.claims.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    @Test
    @DisplayName("blocks requests above maxRequests per IP and resets after the window")
    void rateLimitsPerIpAndResets() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-15T12:00:00Z"));
        AuthRateLimitFilter filter = new AuthRateLimitFilter(new RateLimitProperties(true, 2, 60), clock);

        assertThat(send(filter, "10.0.0.1", "/api/v1/auth/login").getStatus()).isEqualTo(200);
        assertThat(send(filter, "10.0.0.1", "/api/v1/auth/login").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = send(filter, "10.0.0.1", "/api/v1/auth/login");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();

        // Another IP is not affected
        assertThat(send(filter, "10.0.0.2", "/api/v1/auth/login").getStatus()).isEqualTo(200);

        // After the 60s window expires, 10.0.0.1 is allowed again
        clock.advanceSeconds(61);
        assertThat(send(filter, "10.0.0.1", "/api/v1/auth/login").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("does not rate limit non-auth paths")
    void ignoresNonAuthPaths() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                new RateLimitProperties(true, 1, 60), Clock.systemUTC());

        assertThat(send(filter, "10.0.0.1", "/api/v1/claims").getStatus()).isEqualTo(200);
        assertThat(send(filter, "10.0.0.1", "/api/v1/claims").getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse send(AuthRateLimitFilter filter, String ip, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant start) {
            this.current = start;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
