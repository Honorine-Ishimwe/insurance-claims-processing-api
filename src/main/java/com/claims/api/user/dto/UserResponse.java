package com.claims.api.user.dto;

import java.time.Instant;

import com.claims.api.user.AppUser;
import com.claims.api.user.Role;

/** Public view of an account. Never includes the password hash. */
public record UserResponse(Long id, String username, String fullName, Role role, boolean enabled, Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.isEnabled(), user.getCreatedAt());
    }
}
