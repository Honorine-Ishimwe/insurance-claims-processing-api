package com.claims.api.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.claims.api.user.AppUser;
import com.claims.api.user.AppUserRepository;
import com.claims.api.user.Role;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless JWT security.
 * <ul>
 *   <li>{@code POST /api/v1/auth/login}, {@code POST /api/v1/auth/register}, Swagger and
 *       {@code /actuator/health}, {@code /actuator/info} are public.</li>
 *   <li>{@code /api/v1/claims/**} and {@code /api/v1/me/**} require MEMBER.</li>
 *   <li>{@code /api/v1/adjuster/**} requires ADJUSTER.</li>
 *   <li>{@code /api/v1/admin/**} and the other actuator endpoints require ADMIN.</li>
 * </ul>
 * Tokens are HS256-signed and validated by Spring Security's OAuth2 resource server,
 * so there is no hand-written JWT filter to get wrong. On every request the token's user
 * is also checked against the database, so disabling an account takes effect immediately.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            Converter<Jwt, AbstractAuthenticationToken> jwtConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless API, no cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/claims/**", "/api/v1/me/**").hasRole(Role.MEMBER.name())
                        .requestMatchers("/api/v1/adjuster/**").hasRole(Role.ADJUSTER.name())
                        .requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint((req, res, ex) ->
                                writeProblem(res, HttpStatus.UNAUTHORIZED, "Missing, invalid or expired token"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeProblem(res, HttpStatus.FORBIDDEN, "Your role is not allowed to access this resource")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeProblem(res, HttpStatus.UNAUTHORIZED, "Missing, invalid or expired token"))
                        .accessDeniedHandler((req, res, e) ->
                                writeProblem(res, HttpStatus.FORBIDDEN, "Your role is not allowed to access this resource")));
        return http.build();
    }

    /** Maps the token's "roles" claim (e.g. ["MEMBER"]) to authorities (ROLE_MEMBER). */
    private static JwtAuthenticationConverter rolesClaimConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Rejects tokens whose user no longer exists, was disabled, or whose role changed since
     * the token was issued. Costs one indexed lookup per request; worth it for revocation.
     */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> activeUserJwtConverter(AppUserRepository users) {
        JwtAuthenticationConverter delegate = rolesClaimConverter();
        return jwt -> {
            AppUser user = users.findByUsername(jwt.getSubject())
                    .orElseThrow(() -> new DisabledException("Account no longer exists"));
            if (!user.isEnabled()) {
                throw new DisabledException("Account is disabled");
            }
            List<String> tokenRoles = jwt.getClaimAsStringList(TokenService.ROLES_CLAIM);
            if (tokenRoles == null || !tokenRoles.equals(List.of(user.getRole().name()))) {
                throw new DisabledException("Role changed since token was issued");
            }
            return delegate.convert(jwt);
        };
    }

    /**
     * CORS for a future browser frontend. Origins come from CORS_ALLOWED_ORIGINS
     * (comma-separated, e.g. https://app.example.com). Empty means no cross-origin access.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set. Generate one with: openssl rand -base64 48");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.secret().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be Base64. Generate one with: openssl rand -base64 48");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret must decode to at least 32 bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> users.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles(u.getRole().name())
                        .disabled(!u.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    /** Writes an RFC 9457 problem body for errors raised inside the security filter chain. */
    private static void writeProblem(HttpServletResponse res, HttpStatus status, String detail) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
    }
}
