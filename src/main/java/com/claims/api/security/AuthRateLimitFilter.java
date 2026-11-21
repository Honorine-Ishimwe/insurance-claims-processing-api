package com.claims.api.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Brute-force / credential-stuffing protection for the public auth endpoints.
 * A fixed-window counter per client IP; over the limit returns {@code 429 Too Many Requests}
 * with a {@code Retry-After} header.
 * <p>
 * In-memory, so limits are per application instance. If you run several instances behind a
 * load balancer, also enable rate limiting at the gateway / WAF, or move this counter to Redis.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final Set<String> LIMITED_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        long windowMillis = properties.windowSeconds() * 1000L;
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.start >= windowMillis);
        }

        Window window = windows.compute(key, (k, w) ->
                w == null || now - w.start >= windowMillis ? new Window(now, 1) : new Window(w.start, w.count + 1));

        if (window.count > properties.maxRequests()) {
            long retryAfter = Math.max(1, (window.start + windowMillis - now + 999) / 1000);
            log.warn("Rate limit exceeded for {} on {}", request.getRemoteAddr(), request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                    + "\"detail\":\"Too many attempts. Try again in " + retryAfter + " seconds.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(long start, int count) {
    }
}
