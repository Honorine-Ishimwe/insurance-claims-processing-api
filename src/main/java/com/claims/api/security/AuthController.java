package com.claims.api.security;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.claims.api.audit.AuditAction;
import com.claims.api.audit.AuditService;
import com.claims.api.common.NotFoundException;
import com.claims.api.user.AppUser;
import com.claims.api.user.AppUserRepository;
import com.claims.api.user.UserService;
import com.claims.api.user.dto.RegisterRequest;
import com.claims.api.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public endpoints. Both are rate limited per client IP (see AuthRateLimitFilter). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final TokenService tokenService;
    private final UserService userService;
    private final AuditService audit;

    public AuthController(AuthenticationManager authenticationManager, AppUserRepository users,
                          TokenService tokenService, UserService userService, AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.tokenService = tokenService;
        this.userService = userService;
        this.audit = audit;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange username/password for a JWT",
            description = "With the dev profile, demo users are member1 / adjuster1 / admin1, password: password")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            // Throws BadCredentialsException / DisabledException (-> 401, same message either way).
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            audit.recordAs(request.username(), AuditAction.LOGIN_FAILED, "USER", null,
                    ex.getClass().getSimpleName());
            throw ex;
        }
        AppUser user = users.findByUsername(request.username())
                .orElseThrow(() -> new NotFoundException("User not found"));
        TokenService.IssuedToken token = tokenService.issue(user);
        audit.recordAs(user.getUsername(), AuditAction.LOGIN_SUCCEEDED, "USER", user.getId(), null);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), user.getRole().name());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a MEMBER account",
            description = "Self-service sign-up. The new member can log in immediately; an admin must "
                    + "attach a policy (POST /api/v1/admin/policies) before claims can be submitted.")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    public record LoginRequest(
            @Schema(example = "member1") @NotBlank(message = "username is required")
            @Size(max = 100, message = "username is too long") String username,
            @Schema(example = "password") @NotBlank(message = "password is required")
            @Size(max = 200, message = "password is too long") String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, String role) {
    }
}
